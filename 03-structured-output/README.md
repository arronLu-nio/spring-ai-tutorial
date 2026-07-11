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
        .entity(JavaConcept.class);     // JSON → Java 对象
```

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

## 关键 API

| API | 作用 |
|---|---|
| `record` | 定义 AI 结果的 Java 结构 |
| `call()` | 同步等待模型完成回答 |
| `entity(Class)` | 将模型结果转换成指定 Java 类型 |

## 注意

结构化输出依赖模型遵守 JSON 格式。生产环境还需要增加字段校验、异常处理和重试机制。

