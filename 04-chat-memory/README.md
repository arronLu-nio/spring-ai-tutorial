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

可以直接使用 Redis 客户端查看 JSON 消息，用来观察 Memory 是如何从 Redis 读取和写回的。

### Redis metadata 索引配置

模型的 `AssistantMessage` metadata 可能包含数组或对象，例如 `annotations`、`contentFilters` 和 `toolCalls`。Redis Search 默认使用 `$.metadata.*` 通配路径并按 `TEXT` 索引，遇到这些复杂值时会导致索引失败。

本项目直接使用 Spring AI 提供的 `RedisChatMemoryRepository`，通过 `metadataFields(...)` 只索引必要的字符串字段。未配置的 metadata 仍然完整保存在 Redis JSON 中，只是不参与搜索，符合“存储”和“索引”分离的设计。

当前索引字段如下：

```text
$.content         → 全文索引
$.type            → 消息类型
$.conversation_id → 会话 ID（TAG 索引）
$.timestamp       → 消息顺序（数字索引）
$.metadata.role   → 角色（TAG 索引）
```

例如下面这些字段会保存，但不会建立索引：

```json
{
  "annotations": [],
  "index": 0,
  "reasoningContent": ""
}
```

这不会影响按 `conversation_id` 查询历史消息。`content` 的全文索引会增加一些 Redis 内存占用，但当前每个会话最多保留 10 条消息，并且消息 24 小时后过期，适合教程和小规模应用。

Redis 中的 Key 通常类似：

```text
spring-ai-memory:web-demo:1783773093821
```

末尾数字是该会话的递增时间序列，用于保证消息顺序；查询时主要根据 JSON 文档中的 `conversation_id` 索引完成。

保存消息时，`saveAll()` 保存的是当前会话的完整快照。为了配合消息窗口淘汰策略，Repository 会先清理当前会话，再写入最新消息列表，因此 Key 可能重新生成。

本项目使用的新索引名是 `chat-memory-idx-v2`。如果本地 Redis 中还保留旧的 `chat-memory-idx`，可以删除旧索引（不会删除 JSON 消息）：

```bash
REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli FT.DROPINDEX chat-memory-idx
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

## 4. 自定义消息窗口

前面的 `ChatMemory` 默认保留 10 条消息。本节新增了一个独立的 `CustomMemoryController`，只保留最近 4 条消息：

```java
ChatMemory customChatMemory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(4)
        .build();
```

访问接口：

```bash
curl -N "http://localhost:8080/ai/memory/custom-window?conversationId=custom-demo&message=我叫小路"
```

页面中选择“自定义 Memory 窗口（4 条）”即可体验。这里的 4 指消息条数，通常约等于最近 2 轮对话；它与前面的 `web-demo` 使用不同的会话 ID，方便对比两种窗口大小。

## 5. SystemMessage 和普通消息

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

本节还提供了独立接口 `/ai/memory/system-message`：每次请求都会重新注入系统规则，而历史对话由 Memory Advisor 管理。页面中选择“SystemMessage + Memory”即可体验。

```text
本次发送给模型：SystemMessage + 历史 User/Assistant + 当前 UserMessage
本轮保存到 Memory：当前 UserMessage + AssistantMessage
```

## 6. 自定义保留策略：系统消息永不淘汰

如果希望系统规则也由 Memory 统一管理，可以实现一个 `ChatMemory` 包装器：

```text
SystemAwareChatMemory
├── get()：在历史消息最前面补回 SystemMessage
├── add()：只把 User/Assistant 交给 Redis
└── clear()：继续委托给底层 Redis Memory
```

本项目的 `/ai/memory/system-aware` 接口使用这个策略：普通消息最多保留 4 条，但系统消息始终位于最前面。页面中选择“系统消息固定保留”即可体验。
