# 01 - ChatClient：从同步调用到流式输出

## 目标

完成第一个可运行的 AI 接口，并理解同步调用、流式调用和 SSE 的区别。

```text
HTTP 请求 → ChatClient → Chat Model → 大模型 → 文本响应
                                      ├→ call()   → String
                                      └→ stream() → Flux<String> → SSE
```

## 运行

在项目根目录执行：

```bash
export RAG_DEEPSEEK_API_BASE="https://api.deepseek.com"
export RAG_DEEPSEEK_API_KEY="your-deepseek-api-key"
export RAG_DEEPSEEK_MODEL="deepseek-chat"
mvn spring-boot:run
```

## 1. 同步调用

同步接口会等待模型生成完成后，一次性返回完整答案：

```bash
curl "http://localhost:8080/ai/chat?message=用一句话介绍Spring%20AI"
```

核心代码：

```java
return chatClient.prompt()
        .user(message)
        .call()
        .content();
```

## 2. 流式调用

流式接口会边生成边返回内容，适合聊天页面和长文本生成：

```bash
curl -N "http://localhost:8080/ai/chat/stream?message=用三句话介绍Spring%20AI"
```

核心代码：

```java
@GetMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream(@RequestParam String message) {
    return chatClient.prompt()
            .user(message)
            .stream()
            .content();
}
```

`Flux<String>` 表示一组异步到达的文本片段，`text/event-stream` 是浏览器和前端常用的 SSE 响应类型。

## 3. 练习

1. 增加一个固定的 system prompt，让模型始终使用中文回答。
2. 将同步接口改成 POST，并使用请求体传入问题。
3. 用前端 `EventSource` 接收流式响应。
4. 增加统一异常处理，处理模型超时和 API Key 错误。

