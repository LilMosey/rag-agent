# RAG 查询增强 V2 规格

## 背景

V1 已经完成基础 RAG 查询闭环：

```text
会话 -> Router -> 选择知识库 -> Dense 检索 -> 回查 MySQL/MinIO -> LLM 回答 -> 引用展示
```

V2 的目标是在不引入会话记忆和上下文压缩的前提下，提升检索召回能力、检索可调试性和前端回答体验。

## 目标

- 支持 Query 改写。
- 支持 HyDE。
- 支持多 Query。
- 支持 BM25 关键词检索。
- 支持 Dense + BM25 混合检索。
- 支持多路检索并发执行。
- 支持多路检索失败降级。
- 支持按 `chunk_id` 去重。
- 支持 RRF 融合排序。
- 支持检索任务记录，便于分析每一路召回效果。
- 支持检索参数配置化。
- 支持回答流式输出。

## 非目标

- V2 不做上下文压缩。
- V2 不做长期记忆系统。
- V2 不做 `REUSE_LAST_CONTEXT` 真正复用。
- V2 不做 Rerank。
- V2 不做 RAGAS 评估。
- V2 不做前端展示内部 Prompt、改写 Query、HyDE 文本或隐藏上下文。

## 总体链路

```text
用户发送消息
  -> 保存用户消息
  -> Router 判断是否查询知识库、查询哪些知识库
  -> 如果 NO_KB：直接普通聊天流式回答
  -> 如果 SEARCH_KB：
      -> Query 改写
      -> HyDE 生成
      -> 多 Query 生成
      -> 创建多路检索任务
      -> 线程池并发执行 Dense / BM25 检索
      -> 汇总检索结果
      -> 按 chunk_id 去重
      -> RRF 融合排序
      -> 截取 TopK
      -> 回查 MySQL / MinIO 组装上下文
      -> 流式调用大模型生成回答
      -> 保存助手消息
      -> 保存检索任务和引用记录
```

## Query 改写

### 定义

Query 改写是将用户当前问题改写成更适合检索的独立问题。

示例：

```text
历史上下文：用户之前问“差旅住宿标准是多少？”
当前问题：那西安呢？
改写结果：西安出差住宿报销标准是多少？
```

### V2 范围

V2 可以先只使用当前问题和最近若干轮消息做改写，不做长期记忆和压缩摘要。

输入：

- 当前用户问题。
- 当前会话最近 N 条消息。
- Router 的 `queryIntent`。

输出：

```json
{
  "rewrittenQuery": "西安出差住宿报销标准是多少？",
  "changed": true,
  "reason": "补全了追问中的指代对象和检索主题"
}
```

### 使用方式

改写后的 query 参与 Dense 检索。

原始 query 仍然保留并参与检索，避免改写错误导致召回完全偏移。

## HyDE

### 定义

HyDE 是让大模型先基于用户问题生成一段“假设答案”，再使用假设答案做向量检索。

示例：

```text
用户问题：面团为什么放一会儿会变大？
HyDE 文本：面团变大通常是因为酵母发酵产生二氧化碳，气体被面筋网络包裹...
```

### V2 规则

- HyDE 文本只用于检索。
- HyDE 文本不能作为事实依据展示给用户。
- HyDE 失败时，只跳过 HyDE 检索，不影响其他检索任务。
- HyDE 默认可配置开启或关闭。

## 多 Query

### 定义

多 Query 是让大模型生成多个不同角度的检索问题。

示例：

```text
原问题：如何提高睡眠质量？
多 Query：
1. 睡前哪些习惯有助于入睡？
2. 什么样的睡眠环境更适合深度睡眠？
3. 改善失眠有哪些科学方法？
```

### V2 规则

- 多 Query 数量默认 3 个。
- 每个 query 单独执行 Dense 检索。
- 失败时不影响原始 query 和改写 query 检索。
- 多 Query 不直接展示到前端。

## BM25 和关键词检索

### 为什么需要 BM25

Dense 检索擅长语义相似，但对精确词不一定稳定。

BM25 更适合：

- 编号。
- 金额。
- 人名。
- 产品型号。
- 制度条款。
- 专有名词。

例如：

```text
第十七条
iPhone 15 Pro Max
350 元
```

### Milvus 支持方式

Milvus 支持 full text search 和 BM25，但需要 collection schema 支持文本字段、analyzer、sparse vector 和 BM25 function。

当前 V1 的 `enterprise_kb_chunk` 只包含 dense vector，不包含 BM25 所需字段。因此 V2 需要单独设计 schema 迁移。

### 推荐迁移策略

V2 不直接原地修改旧 collection，推荐新建 V2 collection：

```text
enterprise_kb_chunk_v2
```

V2 collection 建议字段：

```text
id                    Int64 AutoID PrimaryKey
knowledge_base_id     Int64
file_id               Int64
chunk_id              Int64
chunk_index           Int32
embedding_source      VarChar
content_hash          VarChar
text                  VarChar enable_analyzer=true
dense_vector          FloatVector
sparse_vector         SparseFloatVector
```

BM25 function：

```text
text -> sparse_vector
```

### 索引写入变化

V2 写入向量时，需要同时写入：

- dense vector
- 用于 BM25 的 text

text 建议使用检索增强文本，但最终溯源仍然使用原始 chunk。

## 混合检索

### 检索任务类型

V2 建议定义以下检索任务类型：

```text
ORIGINAL_DENSE
REWRITE_DENSE
HYDE_DENSE
MULTI_QUERY_DENSE
BM25
```

每一路任务输出统一结构：

```text
retrievalTaskType
queryText
chunkId
score
rank
knowledgeBaseId
fileId
chunkIndex
```

### 并发执行

所有检索任务通过线程池并发执行。

线程池建议独立配置，不复用 Web 容器线程池。

默认配置：

```yaml
rag:
  retrieval:
    executor:
      core-size: 4
      max-size: 8
      queue-capacity: 100
```

### 失败降级

单路检索失败不影响整轮回答。

示例：

- HyDE 生成失败：跳过 HyDE。
- 某个 multi-query 检索失败：跳过该 query。
- BM25 检索失败：保留 Dense 检索结果。
- 全部检索失败：按普通聊天处理，或者返回“知识库检索失败”，具体由配置控制。

V2 默认策略：

```text
至少一路检索成功 -> 使用成功结果回答
全部检索失败 -> 普通聊天回答，并保存失败原因
```

## 去重和融合

### 去重

去重主键：

```text
chunk_id
```

如果同一个 chunk 被多路检索命中，需要保留每一路命中明细。

### RRF 融合

V2 推荐使用 Reciprocal Rank Fusion。

公式：

```text
score = Σ 1 / (k + rank_i)
```

默认：

```text
k = 60
```

RRF 的优点：

- 不依赖不同检索方式的原始分数尺度。
- 适合 Dense 和 BM25 混合。
- 参数少，工程实现稳定。

### 最终 TopK

建议：

```text
denseTopK = 20
bm25TopK = 20
fusionTopK = 10
contextTopK = 5
```

## 检索任务记录

V2 需要新增检索任务记录，用于排查和后续评估。

建议表：

```text
conversation_retrieval_task
```

字段建议：

- `id`
- `conversation_retrieval_id`
- `task_type`
- `query_text`
- `status`
- `error_message`
- `started_at`
- `finished_at`
- `created_at`
- `updated_at`

建议表：

```text
conversation_retrieval_task_hit
```

字段建议：

- `id`
- `retrieval_task_id`
- `knowledge_base_id`
- `file_id`
- `chunk_id`
- `chunk_index`
- `score`
- `rank_no`
- `created_at`

最终引用仍然记录在 V1 已有的：

```text
conversation_retrieval_reference
```

这样可以区分：

- 每一路检索召回了什么。
- 融合后最终给大模型用了什么。

## 配置化

V2 建议新增配置：

```yaml
rag:
  retrieval:
    query-rewrite-enabled: true
    hyde-enabled: true
    multi-query-enabled: true
    bm25-enabled: true
    multi-query-count: 3
    dense-top-k: 20
    bm25-top-k: 20
    fusion-top-k: 10
    context-top-k: 5
    rrf-k: 60
    all-task-failed-fallback: CHAT
    executor:
      core-size: 4
      max-size: 8
      queue-capacity: 100
```

## 流式输出

### 后端接口

V2 新增流式发送接口：

```text
POST /api/conversations/{conversationId}/messages/stream
```

推荐使用 Server-Sent Events。

响应事件建议：

```text
router
retrieval_start
retrieval_done
answer_delta
answer_done
references
error
```

### 前端行为

- 用户发送后立即展示用户消息。
- 后端返回 token 时逐步追加助手消息。
- 回答结束后展示引用来源。
- 失败时展示错误消息。

### 保存时机

建议：

```text
流式开始前保存用户消息
流式过程中缓存 assistant 文本
流式结束后保存完整 assistant 消息
保存 retrieval 和 references
```

如果流式中断：

- 保存已经生成的 assistant 文本。
- 标记错误状态。
- 前端提示回答未完整生成。

## 前端展示边界

V2 前端展示：

- 用户问题。
- AI 流式回答。
- 最终引用来源。

V2 前端不展示：

- Query 改写结果。
- HyDE 文本。
- 多 Query 列表。
- BM25 命中详情。
- RRF 细节。
- 内部 Prompt。

这些内部信息只进入日志或调试接口。

## 日志要求

后端需要记录：

- Query 改写入参和出参。
- HyDE 入参和出参长度。
- 多 Query 生成数量。
- 每一路检索任务开始、结束、耗时、命中数。
- 每一路检索异常堆栈。
- RRF 融合前后数量。
- 最终 contextTopK。
- 流式输出开始、结束、异常。

日志不得输出 API Key。

## 后续版本边界

V3 做：

- 上下文压缩。
- 会话短期记忆。
- 隐藏上下文组装。
- `REUSE_LAST_CONTEXT` 真正生效。
- 复用上一轮引用内容。

V4 做：

- Cross-Encoder Rerank。
- LLM Rerank。
- RAGAS 评估。
- bad case 管理。
- 策略对比实验。

