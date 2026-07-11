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

## 关键 API

| API | 作用 |
|---|---|
| `record` | 定义 AI 结果的 Java 结构 |
| `call()` | 同步等待模型完成回答 |
| `entity(Class)` | 将模型结果转换成指定 Java 类型 |

## 注意

结构化输出依赖模型遵守 JSON 格式。生产环境还需要增加字段校验、异常处理和重试机制。
