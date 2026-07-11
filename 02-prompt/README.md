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

## system 和 user 的区别

| 方法 | 作用 | 示例 |
|---|---|---|
| `system()` | 设定 AI 的角色和规则 | “你是一名 Java 老师” |
| `user()` | 传入用户当前的问题 | “什么是 Flux？” |

## 练习

把 `system()` 中的提示词改成：

```text
你是一名严格的代码审查专家。请从正确性、可读性和安全性三个方面分析用户的问题。
```

然后观察 AI 的回答风格有什么变化。

