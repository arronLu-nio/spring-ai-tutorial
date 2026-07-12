# 06 - Production RAG：从 Demo 到生产实践

第五章解决了“RAG 能不能跑起来”，这一章解决“RAG 在真实业务中如何稳定运行”。

## 本章路线

```text
多轮上下文
  ↓
Query 改写
  ↓
缓存、超时、重试、降级
  ↓
文档版本管理
  ↓
可观测性和成本统计
  ↓
权限过滤和自动化评估
```

## 已完成内容

### 1. 多轮上下文 RAG

接口：

```text
GET /api/rag/chat
```

请求示例：

```bash
curl --get http://localhost:8080/api/rag/chat \
  --data-urlencode "conversationId=rag-demo" \
  --data-urlencode "question=刚才提到的线程池拒绝策略怎么选择？"
```

处理流程：

```text
conversationId
  ↓
Redis 读取最近历史
  ↓
QueryRewriteService 补全省略指代
  ↓
Milvus + OpenSearch 检索
  ↓
带引用生成回答
  ↓
保存本轮消息
```

历史消息只取最近一部分，不会把整个会话无限传给模型。

### 2. Query 改写缓存

Query 改写需要额外调用一次模型，因此使用 Redis 缓存改写结果。

缓存 Key 包含：

```text
conversationId + question + conversationHistory
```

这样同一个问题在不同会话或不同上下文中不会共用错误结果。

### 3. 超时、重试和降级

统一保护层位于 `RagResilienceService`：

```text
外部调用
  ↓ 超时控制
失败？
  ↓
有限重试
  ↓ 仍然失败
安全降级
```

当前保护对象：

- Query 改写模型
- DashScope Rerank
- 最终回答模型

配置：

```properties
RAG_TIMEOUT_SECONDS=20
RAG_MAX_RETRIES=2
RAG_QUERY_CACHE_TTL_SECONDS=300
```

降级策略：

- Query 改写失败：使用原问题继续检索
- Rerank 失败：使用 RRF 混合检索结果
- 最终回答失败：返回安全提示，不泄露内部异常

### 4. 引用和检索评估基础

回答中的 `[1]`、`[2]` 对应接口返回的 `sources[].citation`。

单问题评估接口：

```bash
curl --get http://localhost:8080/api/rag/evaluate \
  --data-urlencode "question=线程池有哪些拒绝策略？" \
  --data-urlencode "expectedSource=juc-interview-question.md"
```

返回 `hit` 和 `reciprocalRank`，用于检查正确来源是否被召回。

## 暂时跳过的内容

### 权限过滤

生产环境需要在检索前根据租户、部门、角色和用户身份过滤文档。当前教程暂时不接入真实业务权限系统，避免用一个简单示例制造不安全的伪权限方案。

### 批量评测集

批量评测需要业务人员提供“问题、标准答案、正确来源”数据。当前只保留单问题评估接口，等有真实业务数据后再实现评测集导入和批量统计。

### 限流和并发保护

当前先完成单请求级别的超时、重试和降级；全局限流、用户级限流和线程池隔离后续单独学习。

## 文档增量更新和版本管理

生产环境通常给文档和切片增加：

```text
documentId
version
contentHash
status
```

新版本完整写入 Milvus 和 OpenSearch 后，再标记为 `ACTIVE`，旧版本标记为 `INACTIVE` 或延迟清理。新版本处理失败时，继续使用旧版本，避免知识库短暂不可用。

## 可观测性和成本统计

Spring AI 和 Spring Boot 可以通过 Micrometer、Actuator 和 Observation 记录模型、Embedding、VectorStore 的调用指标。业务层仍需要自行补充：

- RAG 命中率
- 引用准确率
- Query 改写命中率
- 降级次数
- 输入和输出 Token
- 按接口、用户、模型统计成本

这部分后续可以接入 Prometheus 和 Grafana。

## 关键代码

- `RagService`：多轮 RAG、引用、召回和回答
- `QueryRewriteService`：上下文 Query 改写和 Redis 缓存
- `RagResilienceService`：超时、重试、降级
- `DashScopeReranker`：Rerank 和 RRF 回退
- `RagController`：RAG 上传、问答和评估接口

