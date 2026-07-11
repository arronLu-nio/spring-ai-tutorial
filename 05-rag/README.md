# 05 - RAG：检索增强生成

本章实现一个可以运行的企业知识库最小闭环：上传文档、切分、向量化、混合检索、重排，最后让大模型基于召回内容回答问题。

## 整体流程

```text
上传文件
  ↓
Tika 解析文本
  ↓
TokenTextSplitter 切分
  ↓
DashScope Embedding
  ↓
Milvus 向量检索 + OpenSearch BM25 检索
  ↓
RRF 融合
  ↓
DashScope Rerank（可选）
  ↓
DeepSeek 根据参考资料回答
```

## 启动前检查

```text
Milvus：localhost:19530
OpenSearch：127.0.0.1:9200
```

如果使用 Docker，至少需要确认两个服务的端口已经映射到宿主机：

```bash
docker ps
curl http://127.0.0.1:9200
curl http://127.0.0.1:9091/api/v1/collections
```

本教程使用 Milvus 集合 `rag_tutorial_chunks`。如果本地已经存在名为
`rag_chunks` 的企业 RAG 集合，不要直接复用它：它的字段结构通常包含
`tenant_id`、`page` 等额外字段，与 Spring AI 默认 schema 不兼容。

OpenSearch 同样使用独立索引 `rag_tutorial_chunks`，不要复用已有的
`rag_chunks` 索引，否则会因为 `id`、`chunk_id` 等字段差异导致解析异常。

如果通过环境变量或 Nacos 配置覆盖了 `RAG_MILVUS_CHUNKS_COLLECTION`，请将它设置为：

```bash
export RAG_MILVUS_CHUNKS_COLLECTION=rag_tutorial_chunks
export RAG_OPENSEARCH_INDEX=rag_tutorial_chunks
```

`initialize-schema: true` 只负责首次创建集合，不会修改已经存在的集合字段。

如果配置放在 Nacos，请修改 Data ID `spring-ai-tutorial.properties`、Group `DEFAULT_GROUP`：

```properties
RAG_MILVUS_CHUNKS_COLLECTION=rag_tutorial_chunks
RAG_OPENSEARCH_INDEX=rag_tutorial_chunks
```

Embedding 使用 OpenAI 兼容接口配置：

```bash
export RAG_EMBEDDING_PROVIDER=dashscope
export RAG_EMBEDDING_API_BASE="你的 DashScope 兼容接口地址"
export RAG_EMBEDDING_API_KEY="你的 DashScope Key"
export RAG_EMBEDDING_MODEL=text-embedding-v4
export RAG_EMBEDDING_DIMENSIONS=1024
```

## 页面

启动应用后打开：

```text
http://localhost:8080/rag.html
```

先上传 PDF、Word 或文本文件，再输入问题。

上传成功后，可以在 OpenSearch 中确认切片数量：

```bash
curl "http://127.0.0.1:9200/rag_tutorial_chunks/_count"
```

## API

```bash
curl -F "file=@./demo.pdf" http://localhost:8080/api/rag/documents
curl --get http://localhost:8080/api/rag/ask \
  --data-urlencode "question=公司的报销流程是什么？"
```

预期结果包含 `answer` 和 `sources`：

```json
{
  "answer": "...",
  "sources": [
    {
      "source": "demo.pdf",
      "content": "..."
    }
  ]
}
```

## 关键代码

- `RagIngestionService`：文件解析、切分和写入两个检索引擎
- `OpenSearchChunkIndex`：BM25 关键词检索
- `RagService`：Milvus 向量检索、RRF 融合、Rerank 和生成回答
- `RagController`：上传和问答接口

## 常见问题

### 1. `The field: id is not provided`

通常是因为复用了已有的 Milvus 集合。Spring AI 默认集合字段是 `doc_id`、`content`、`metadata` 和 `embedding`，而企业 RAG 集合可能使用 `id`、`text`、`tenant_id` 等不同字段。

请使用独立集合：

```properties
RAG_MILVUS_CHUNKS_COLLECTION=rag_tutorial_chunks
```

### 2. `MissingNode ... asString()`

通常是因为复用了已有的 OpenSearch 索引，旧索引可能只有 `chunk_id` 而没有教程代码读取的 `id`。请使用独立索引：

```properties
RAG_OPENSEARCH_INDEX=rag_tutorial_chunks
```

### 3. Embedding 报错 `batch size ... should not be larger than 10`

DashScope Embedding 单次最多接收 10 条文本。`RagIngestionService` 已经按每批 10 个切片循环调用 Milvus，不需要修改文本切分策略。

### 4. 修改配置后仍然不生效

优先检查配置来源：环境变量和 Nacos 会覆盖 `application.yml`。修改后必须重启 Spring Boot。

当前示例不把密钥写入代码或 Git，只从环境变量读取。
