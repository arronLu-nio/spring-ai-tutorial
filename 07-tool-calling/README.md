# 07 - Tool Calling：让大模型调用 Java 方法

Tool Calling 不是让大模型直接执行 Java 代码，而是让模型先声明“想调用哪个工具、传什么参数”，Spring AI 再负责校验参数、执行 Java 方法，并把执行结果交回模型。

## 学完这一章你会掌握

- 使用 `@Tool` 暴露 Java 方法
- 使用 `@ToolParam` 描述工具参数
- 让模型自动选择工具
- 理解 Assistant tool call、ToolResponseMessage 和最终回答
- 在流式输出和多轮对话中使用工具
- 把工具消息保存到 Redis
- 在真正执行工具前做权限校验

## 调用链路

```text
用户问题
  ↓
ChatClient 把工具定义发送给模型
  ↓
模型返回 tool call：工具名 + JSON 参数
  ↓
Spring AI 查找并执行对应 Java 方法
  ↓
生成 ToolResponseMessage
  ↓
把工具结果再次发送给模型
  ↓
模型生成最终回答
```

例如用户问：

```text
现在上海几点？
```

模型可能先返回：

```json
{
  "name": "getCurrentTime",
  "arguments": {
    "zoneId": "Asia/Shanghai"
  }
}
```

这还不是最终答案，只是模型提出的工具调用请求。

## 1. 定义工具

工具就是一个普通 Java 类的方法，加上 `@Tool`：

```java
public class CalculatorTool {

    @Tool(description = "计算两个整数的和，只用于简单加法，不执行任意代码")
    public String add(
            @ToolParam(description = "第一个整数", required = true) int left,
            @ToolParam(description = "第二个整数", required = true) int right) {
        return String.valueOf(Math.addExact(left, right));
    }
}
```

`description` 很重要。模型主要依靠工具描述判断什么时候应该调用这个方法。

参数描述同样重要：

```java
@ToolParam(description = "Java 时区 ID，例如 Asia/Shanghai 或 UTC", required = true)
String zoneId
```

Spring AI 会根据方法和参数生成工具 schema，并将 schema 发送给模型。

当前示例工具位于：

- `CurrentTimeTool`：查询指定时区的时间
- `JavaInfoTool`：查询当前 Java 运行时信息
- `CalculatorTool`：执行两个整数的加法

## 2. 把工具注册到 ChatClient

```java
return chatClient.prompt()
        .tools(new CurrentTimeTool(), new JavaInfoTool(), new CalculatorTool())
        .user(message)
        .stream()
        .content();
```

`.tools(...)` 做了三件事：

1. 读取工具方法和注解
2. 生成工具名称、描述和参数 schema
3. 把工具定义放入本次模型请求

工具注册不等于工具已经执行。只有模型返回 tool call，并且通过执行检查后，Java 方法才会真正运行。

## 3. 工具调用和最终回答

一次完整调用通常包含多轮模型消息：

```text
UserMessage
  ↓
AssistantMessage：请求调用 getCurrentTime
  ↓
ToolResponseMessage：返回当前时间
  ↓
AssistantMessage：根据时间组织最终回答
```

因此，工具调用通常会产生至少两次模型请求：

- 第一次：判断是否需要工具，并生成参数
- 第二次：读取工具结果，生成自然语言回答

## 4. 和 Memory 一起使用

当前示例接口：

```text
GET /ai/memory/chat
```

请求参数：

```text
conversationId=web-demo
message=现在上海几点？
```

核心代码：

```java
.system("你是一名耐心的 Java 和 Spring AI 老师，请使用简单的中文回答。")
.tools(new CurrentTimeTool(), new JavaInfoTool(), new CalculatorTool())
.advisors(advisorSpec -> advisorSpec
        .advisors(memoryAdvisor)
        .advisors(toolMessageSavingAdvisor)
        .param(ChatMemory.CONVERSATION_ID, conversationId))
.advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
.user(message)
.stream()
.content();
```

`conversationId` 决定从 Redis 读取和保存哪一段对话。

## 5. 为什么要保存 ToolMessage

如果只保存用户消息和最终回答，下一轮模型看不到中间的工具调用过程：

```text
用户：上海现在几点？
助手：现在是……
```

完整保存时，Redis 中还会有：

```text
USER
ASSISTANT：tool call getCurrentTime
TOOL：getCurrentTime 返回结果
ASSISTANT：最终回答
```

本项目的 `ToolMessageSavingAdvisor` 会在工具执行完成后保存：

```java
List<Message> history = result.conversationHistory();
chatMemory.add(conversationId,
        history.subList(history.size() - 2, history.size()));
```

这里保存的是工具调用 AssistantMessage 和 ToolResponseMessage，不是再次调用大模型。

## 6. 权限校验应该在哪里做

不要把所有工具都无条件暴露给模型。生产环境建议分三层：

```text
工具是否展示给模型
  ↓
模型是否请求调用
  ↓
服务端是否允许执行
```

例如销售 A 查询客户 B：

```text
用户输入查询客户 B
  ↓
模型请求 customer.get
  ↓
服务端读取当前登录用户 A
  ↓
校验客户 B 是否属于销售 A
  ↓
无权限：拒绝执行，不访问数据库
```

最终权限判断必须在服务端工具方法或工具执行层完成，不能依赖模型自己判断。

## 7. 常见问题

### 模型为什么没有调用工具？

- 工具描述不清晰
- 用户问题和工具职责不匹配
- 当前模型不支持 Tool Calling
- 工具没有注册到本次请求
- API 兼容层没有正确传递 `tools` 参数

### 工具执行失败怎么办？

工具方法应该返回可理解的错误信息，或者抛出受控异常。不要把数据库堆栈、密钥和内部路径直接返回给模型。

### Tool Calling 会增加成本吗？

会。通常至少多一次模型请求，还会增加工具 schema 和工具结果的 token。

### 能不能让模型执行任意 Java 代码？

不能。只允许调用明确注册、参数受限、权限可控的工具。不要提供执行脚本、拼接 SQL 或任意反射调用的工具。

## 8. 动手练习

1. 增加一个 `WeatherTool`，只允许查询固定城市。
2. 给 `CalculatorTool` 增加除法，并处理除数为 0。
3. 让工具返回结构化 DTO，而不是拼接字符串。
4. 给客户查询工具增加销售人员权限校验。
5. 在 Redis 中观察一次完整 Tool Calling 的四类消息。

## 关键代码位置

- 工具：`src/main/java/com/example/springaitutorial/tool/`
- 工具记忆：`src/main/java/com/example/springaitutorial/advisor/ToolMessageSavingAdvisor.java`
- 多轮对话入口：`src/main/java/com/example/springaitutorial/controller/MemoryController.java`

