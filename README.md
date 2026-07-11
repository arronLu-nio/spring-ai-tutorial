# Spring AI Tutorial

一个边学边实践的 Spring AI 教程，示例均可独立运行。

## 学习路线

1. ChatClient：调用大模型
2. Prompt：设计提示词
3. Structured Output：结构化输出
4. Chat Memory：多轮对话
5. RAG：基于知识库问答
6. Tool Calling：让模型调用业务工具
7. Observability：日志、指标与成本追踪

## 环境要求

- Java 21+
- Maven 3.9+
- Spring Boot 4.0.x
- Spring AI 2.0.0
- 一个 OpenAI 兼容接口的 API Key

## 快速开始

```bash
export OPENAI_API_KEY="your-api-key"
mvn spring-boot:run
```

启动后访问：

```bash
curl "http://localhost:8080/ai/chat?message=什么是Spring%20AI？"
```

## 章节目录

| 章节 | 内容 | 状态 |
|---|---|---|
| [01-chatclient](./01-chatclient) | 使用 ChatClient 调用大模型 | ✅ |
| 02-prompt | Prompt 设计 | 🚧 |
| 03-structured-output | 结构化输出 | 🚧 |
| 04-chat-memory | 多轮对话 | 🚧 |
| 05-rag | RAG 知识库问答 | 🚧 |
| 06-tool-calling | Tool Calling | 🚧 |
| 07-observability | 可观测性 | 🚧 |

## 许可证

本项目采用 MIT License。

