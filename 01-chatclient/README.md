# 01 - 使用 ChatClient 调用大模型

## 目标

理解 Spring AI 中最核心的调用链：

```text
HTTP 请求 → ChatClient → Chat Model → 大模型 → 文本响应
```

## 运行

在项目根目录执行：

```bash
export OPENAI_API_KEY="your-api-key"
mvn spring-boot:run
```

然后调用：

```bash
curl "http://localhost:8080/ai/chat?message=用一句话介绍Spring%20AI"
```

## 核心代码

```java
return chatClient.prompt()
        .user(message)
        .call()
        .content();
```

`ChatClient` 提供了面向 Spring 开发者的 Fluent API。后续章节会在这条调用链上加入 Prompt、记忆、RAG 和工具调用。

## 练习

1. 增加一个固定的 system prompt，让模型始终使用中文回答。
2. 增加 `temperature` 参数，观察回答风格变化。
3. 将接口改成 POST，并使用请求体传入问题。

