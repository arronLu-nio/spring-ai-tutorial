# 03 - Structured Output：结构化输出

## 本节目标

让 AI 返回可以直接被 Java 程序使用的对象，而不是手动解析一段字符串。

## 核心代码

定义结果结构：

```java
public record JavaConcept(
        String name,
        String definition,
        String analogy,
        List<String> examples
) {
}
```

调用 `entity()` 映射成 Java 对象：

```java
return chatClient.prompt()
        .system("请用中文解释 Java 或 Spring 概念，内容要适合初学者。")
        .user("请解释这个概念：" + topic)
        .call()                         // 同步等待完整结果
        // JavaConcept.class 告诉 Spring AI 目标对象的字段结构
        // Spring AI 会要求模型返回 JSON，再把 JSON 反序列化为 JavaConcept
        .entity(JavaConcept.class);
```

### `entity()` 底层做了什么？

```text
JavaConcept.class
      │
      ▼
生成结构化输出要求
      │
      ▼
大模型返回 JSON 文本
      │
      ▼
StructuredOutputConverter
      │
      ▼
JavaConcept 对象
```

它不是简单地把字符串强制转换成对象，而是经过以下过程：

1. 根据 `JavaConcept` 的字段生成输出结构
2. 将结构要求传递给大模型
3. 接收模型生成的 JSON 文本
4. 通过转换器将 JSON 反序列化
5. 返回可以直接使用的 Java 对象

## 运行

```bash
export RAG_DEEPSEEK_API_KEY="your-deepseek-api-key"
mvn spring-boot:run
```

调用接口：

```bash
curl "http://localhost:8080/ai/structured-output?topic=Flux"
```

返回结果类似：

```json
{
  "name": "Flux",
  "definition": "Flux 是 Reactor 中表示多个异步数据的类型。",
  "analogy": "它像一条持续流动的数据管道。",
  "examples": ["Flux<String> messages;"]
}
```

页面中选择“结构化输出模式”即可体验。

## 2. JSON 解析异常处理

模型不一定每次都返回合法 JSON，直接调用 `entity()` 可能抛出异常。对外接口应该捕获异常并返回统一的错误信息：

```java
try {
    JavaConcept result = chatClient.prompt()
            .user("请解释这个概念：" + topic)
            .call()
            .entity(JavaConcept.class);
    return ResponseEntity.ok(result);
} catch (Exception exception) {
    log.error("结构化输出解析失败，topic={}", topic, exception);
    return ResponseEntity.internalServerError().body(Map.of(
            "error", "STRUCTURED_OUTPUT_PARSE_FAILED",
            "message", "AI 返回的内容无法转换成 JavaConcept，请稍后重试"
    ));
}
```

调用安全接口：

```bash
curl "http://localhost:8080/ai/structured-output/safe?topic=Flux"
```

页面中选择“结构化输出（异常处理）”即可体验。

> 教程中先使用通用 `Exception` 让流程更容易理解。生产项目中应进一步区分 API 超时、模型调用失败、JSON 转换失败等异常类型。

## 3. 字段校验

JSON 能成功解析，不代表字段内容一定符合业务要求。可以使用 Jakarta Bean Validation 给 Java 类型增加约束：

```java
public record JavaConcept(
        @NotBlank(message = "概念名称不能为空")
        String name,
        @NotBlank(message = "概念定义不能为空")
        String definition,
        @NotBlank(message = "概念类比不能为空")
        String analogy,
        @NotEmpty(message = "至少需要一个代码示例")
        @Size(max = 5, message = "代码示例不能超过 5 个")
        List<String> examples
) {
}
```

拿到 AI 对象后执行校验：

```java
Set<ConstraintViolation<JavaConcept>> violations = validator.validate(result);
if (!violations.isEmpty()) {
    // 返回字段校验失败信息，不把不合格的数据交给业务层
}
```

页面中选择“结构化输出（字段校验）”即可体验。

## 4. 失败重试

Spring AI 可以在 JSON Schema 校验失败后，把具体错误反馈给模型并自动重新请求：

```java
JavaConcept result = chatClient.prompt()
        .user("请解释这个概念：" + topic)
        .call()
        // 默认最多重试 3 次；需要完整响应，不支持 stream()
        .entity(JavaConcept.class, spec -> spec.validateSchema());
```

调用接口：

```bash
curl "http://localhost:8080/ai/structured-output/retry?topic=Flux"
```

页面中选择“结构化输出（自动重试）”即可体验。

重试流程：

```text
模型返回
   ↓
Schema 校验
   ├── 通过 → 返回 JavaConcept
   └── 失败 → 把错误反馈给模型 → 再次请求
```

## 关键 API

| API | 作用 |
|---|---|
| `record` | 定义 AI 结果的 Java 结构 |
| `call()` | 同步等待模型完成回答 |
| `entity(Class)` | 将模型结果转换成指定 Java 类型 |

## 注意

结构化输出依赖模型遵守 JSON 格式。生产环境还需要增加字段校验、异常处理和重试机制。
