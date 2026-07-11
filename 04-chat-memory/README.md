# 04 - Chat Memory：多轮对话

## 本节目标

理解大模型默认是无状态的，并使用 `ChatMemory` 保存同一个会话的上下文。

## 核心代码

```java
return chatClient.prompt()
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

当前使用的是内存存储，应用重启后历史会消失。后续会学习 Redis 和数据库持久化。

