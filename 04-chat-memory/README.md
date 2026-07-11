# 04 - Chat Memory：多轮对话

## 本节目标

理解大模型默认是无状态的，并使用 `ChatMemory` 保存同一个会话的上下文。

## 核心代码

```java
return chatClient.prompt()
        // SystemMessage：定义本次会话的角色和规则
        .system("你是一名耐心的 Java 和 Spring AI 老师，请使用简单的中文回答。")
        .advisors(advisorSpec -> advisorSpec
                // 加入记忆 Advisor
                .advisors(memoryAdvisor)
                // 指定会话 ID，决定读取哪段历史
                .param(ChatMemory.CONVERSATION_ID, conversationId))
        .user(message)
        .stream()
        .content();
```

## 运行

```bash
export RAG_DEEPSEEK_API_KEY="your-deepseek-api-key"
mvn spring-boot:run
```

连续发送两次请求，第二次会带上第一次的上下文：

```bash
curl -N "http://localhost:8080/ai/memory/chat?conversationId=demo-1&message=我叫小明"
curl -N "http://localhost:8080/ai/memory/chat?conversationId=demo-1&message=我叫什么名字？"
```

换一个 `conversationId`，模型就不会读取 `demo-1` 的历史：

```bash
curl -N "http://localhost:8080/ai/memory/chat?conversationId=demo-2&message=我叫什么名字？"
```

## 工作流程

```text
conversationId
      ↓
读取历史消息
      ↓
历史消息 + 当前问题 → 大模型
      ↓
保存本轮用户消息和 AI 回复
```

当前使用 Redis Stack 保存消息，应用重启后会话仍然存在。

## 2. Redis 持久化

项目连接本机 Redis Stack：

```text
host: localhost
port: 6379
```

启动前设置 Redis 密码：

```bash
export REDIS_PASSWORD="your-redis-password"
mvn spring-boot:run
```

项目使用 `RedisChatMemoryRepository` 保存消息，再由 `MessageWindowChatMemory` 控制每个会话最多保留 10 条消息：

```java
@Bean
public ChatMemory chatMemory(RedisChatMemoryRepository repository) {
    return MessageWindowChatMemory.builder()
            .chatMemoryRepository(repository)
            .maxMessages(10)
            .build();
}
```

默认 TTL 是 24 小时，配置在 `application.yml`：

```yaml
spring:
  ai:
    chat:
      memory:
        repository:
          redis:
            time-to-live: 24h
```

## 3. 配置记忆窗口

Spring AI 默认的 `MessageWindowChatMemory` 会保留一个有限的消息窗口。我们也可以手动配置窗口大小：

```java
@Bean
public ChatMemory chatMemory() {
    return MessageWindowChatMemory.builder()
            // 最多保留 10 条消息，超出后淘汰较早的消息
            .maxMessages(10)
            .build();
}
```

这里的 10 指消息数量，不是对话轮数。一轮对话通常包含一条用户消息和一条 AI 消息，因此 10 条消息大约可以保存 5 轮对话。

窗口的作用是控制发送给模型的上下文大小，避免历史越来越长导致：

- Token 消耗持续增加
- 请求速度变慢
- 超出模型上下文窗口
- 旧信息影响当前回答

页面中选择“Memory 多轮对话”，连续提问即可体验会话窗口。

流式响应需要等所有文本片段返回完成后，才能拼接成完整的 `AssistantMessage` 并保存到 Redis。当前示例在 `doOnComplete()` 中完成这一步：

```java
.doOnNext(assistantResponse::append)
.doOnComplete(() -> chatMemory.add(
        conversationId,
        new AssistantMessage(assistantResponse.toString())))
```

## 4. SystemMessage 和普通消息

Memory 中常见的消息类型包括：

```text
SystemMessage     → 系统规则和角色
UserMessage       → 用户问题
AssistantMessage  → AI 回复
```

`SystemMessage` 不是用户提问，也不是 AI 回复，而是告诉模型“应该如何工作”。在当前 `MessageChatMemoryAdvisor` 的实现中，`system()` 产生的系统消息会被放到本次请求的最前面，但 Advisor 默认只把当前用户消息和模型回复写入 Memory；因此它不会占用下面 10 条历史消息的窗口。

```text
Memory 中保存：UserMessage + AssistantMessage
本次发送给模型：SystemMessage + Memory 历史 + 当前 UserMessage
```

如果你手动把 `SystemMessage` 写入 `ChatMemory`，`MessageWindowChatMemory` 才会对它做特殊保留处理。
