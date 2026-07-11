# 02 - Prompt 工程：system 和 user

## 本节目标

学会给 AI 设定角色和行为规则，并向它传入用户问题。

## 运行

```bash
export RAG_DEEPSEEK_API_KEY="your-deepseek-api-key"
mvn spring-boot:run
```

调用第二章接口：

```bash
curl "http://localhost:8080/ai/prompt?question=Flux%3CString%3E%20是什么？"
```

## 核心代码

```java
return chatClient.prompt()
        // 规定 AI 是谁，以及应该如何回答
        .system("你是一名耐心的 Java 和 Spring AI 老师，请使用简单的中文回答小白问题")
        // 传入用户当前的问题
        .user(question)
        // 等待模型生成完整结果
        .call()
        // 提取文本内容
        .content();
```

## 3. Prompt 模板

当 Prompt 的结构固定、只有部分内容变化时，可以使用 `{变量名}` 定义模板，再通过 `param()` 注入变量：

```java
return chatClient.prompt()
        .user(user -> user
                // {topic} 是模板变量
                .text("请用简单的中文解释 {topic}，并给出一个 Java 示例。")
                // 运行时将 topic 的值填入模板
                .param("topic", topic))
        .call()
        .content();
```

调用模板接口：

```bash
curl -N "http://localhost:8080/ai/prompt/template?topic=Flux%3CString%3E"
```

这个接口使用 `Flux<String>` 流式返回。页面中选择“Prompt 模板模式”，也可以直接体验这个接口。

## 4. Few-shot Prompt

Few-shot 的意思是：先给 AI 一两个输入输出示例，再让它处理新的问题。示例可以帮助模型理解你想要的回答格式。

```java
return chatClient.prompt()
        .system("""
                你是一个 Java 概念解释助手。
                请严格参考下面的示例，用“定义 + 类比 + 示例”三个部分回答。

                示例问题：什么是 String？
                示例回答：
                定义：String 是 Java 中表示字符串的类型。
                类比：它像一个可以存放文字的盒子。
                示例：String name = "Spring AI";
                """)
        .user(question)
        .stream()
        .content();
```

调用接口：

```bash
curl -N "http://localhost:8080/ai/prompt/few-shot?question=什么是Flux？"
```

页面中选择“Few-shot 示例模式”即可体验。

## 5. 输出格式约束

除了规定回答内容，还可以规定回答的结构。例如要求 AI 固定输出“结论、解释、示例”三个部分：

```java
return chatClient.prompt()
        .system("""
                请严格按照下面的 Markdown 格式回答，不要增加其他标题：

                ## 结论
                用一句话回答问题。

                ## 解释
                用两到三句话解释原因。

                ## 示例
                提供一段最小可运行的 Java 代码。
                """)
        .user(question)
        .stream()
        .content();
```

调用接口：

```bash
curl -N "http://localhost:8080/ai/prompt/format?question=什么是ChatClient？"
```

页面中选择“格式约束模式”即可体验。

> 本节的格式约束仍然是普通文本。下一章会学习如何让 AI 返回可以直接映射成 Java 对象的结构化数据。

## system 和 user 的区别

| 方法 | 作用 | 示例 |
|---|---|---|
| `system()` | 设定 AI 的角色和规则 | “你是一名 Java 老师” |
| `user()` | 传入用户当前的问题 | “什么是 Flux？” |
| `text()` | 定义带变量的 Prompt 文本 | “请解释 {topic}” |
| `param()` | 给模板变量传入实际值 | `param("topic", topic)` |

## 练习

把 `system()` 中的提示词改成：

```text
你是一名严格的代码审查专家。请从正确性、可读性和安全性三个方面分析用户的问题。
```

然后观察 AI 的回答风格有什么变化。
