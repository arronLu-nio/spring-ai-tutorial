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
