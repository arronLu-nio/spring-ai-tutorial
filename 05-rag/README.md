# 05 - RAG：检索增强生成

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

## API

```bash
curl -F "file=@./demo.pdf" http://localhost:8080/api/rag/documents
curl --get http://localhost:8080/api/rag/ask \
  --data-urlencode "question=公司的报销流程是什么？"
```

## 关键代码

- `RagIngestionService`：文件解析、切分和写入两个检索引擎
- `OpenSearchChunkIndex`：BM25 关键词检索
- `RagService`：Milvus 向量检索、RRF 融合、Rerank 和生成回答
- `RagController`：上传和问答接口

当前示例不把密钥写入代码或 Git，只从环境变量读取。
