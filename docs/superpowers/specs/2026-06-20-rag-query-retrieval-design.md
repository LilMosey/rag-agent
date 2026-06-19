# RAG 查询检索第一版规格

## 背景

当前项目已经完成知识库文件管理、文档解析、文本清洗、分块、分块增强、Embedding 生成，以及向量写入 Milvus。

下一步进入 RAG 在线查询阶段。第一版目标不是一次性做完整的高级 RAG，而是先打通稳定、可溯源、可调试的查询闭环：

```text
用户提问 -> 判断是否查询知识库 -> 选择知识库 -> 向量检索 -> 读取原文 -> 大模型回答 -> 返回引用来源
```

## 目标

- 支持聊天窗口式多轮会话。
- 不同会话窗口之间上下文隔离。
- 每轮用户提问先经过知识库路由判断。
- 知识库路由参考知识库名称和描述，判断是否需要查询知识库，以及应该查询哪些知识库。
- 第一版只实现稠密向量检索。
- 检索时只在路由选中的知识库范围内查询 Milvus。
- 检索命中后，根据 `chunk_id` 回查 MySQL 和 MinIO，还原原始 chunk 正文和溯源信息。
- 使用大模型基于检索原文生成回答。
- 保存用户消息、助手消息、路由结果和本轮引用记录。
- 前端提供基础聊天窗口和引用来源展示。

## 非目标

- 本期不做 HyDE。
- 本期不做 Query 改写。
- 本期不做 BM25、关键词检索和混合检索。
- 本期不做 Rerank。
- 本期不做上下文压缩。
- 本期不做 Agent 长期记忆。
- 本期不做用户画像、偏好记忆或跨会话记忆。
- 本期不做 Graph RAG。
- 本期不做自动评测体系。

## 核心原则

### 知识库像 Skill 一样被路由

每个知识库都具备名称和描述，这些信息可以作为大模型判断依据。

示例：

```text
知识库 ID：2
名称：公司差旅制度
描述：包含员工出差住宿标准、交通费、报销时限、发票要求、审批流程等制度内容。
```

用户提问：

```text
西安出差住宿能报销多少？
```

路由模型应判断该问题需要查询“公司差旅制度”知识库。

### 溯源正文必须来自原始 chunk

分块增强内容、摘要、模拟问题可以用于提升检索召回，但最终返回给用户的引用来源应以原始 chunk 正文为准。

回答生成时可以带上：

- 文件名
- 文件类型
- 标题路径
- chunk 序号
- 原始 chunk 正文

不应把大模型生成的摘要或模拟问题当成原文证据展示给用户。

### 第一版优先稳定闭环

第一版只做 Dense Retrieval，不引入多路检索、HyDE、Query 改写和 Rerank。

这样可以先验证：

- Router 是否能选对知识库。
- 当前 embedding 和 Milvus 检索是否能召回相关 chunk。
- 溯源链路是否完整。
- Prompt 是否能约束模型基于证据回答。

## 查询链路

### 主流程

```text
用户在某个会话窗口提问
  -> 保存用户消息
  -> 查询当前可用知识库名称和描述
  -> 调用 RAG Router
  -> Router 返回 action、knowledgeBaseIds、queryIntent、confidence、reason
  -> 如果 action = NO_KB，直接调用大模型普通回答
  -> 如果 action = REUSE_LAST_CONTEXT，本期统一降级为 SEARCH_KB
  -> 如果 action = SEARCH_KB，对当前用户问题生成 embedding
  -> Milvus 按 knowledge_base_id 过滤并进行向量检索
  -> 根据命中的 chunk_id 批量查询 MySQL
  -> 从 MinIO 读取原始 chunk 正文
  -> 组装 RAG Prompt
  -> 调用大模型生成回答
  -> 保存助手消息
  -> 保存本轮引用记录
  -> 返回回答和引用来源
```

### Router 输出

Router 建议返回 JSON：

```json
{
  "action": "SEARCH_KB",
  "knowledgeBaseIds": [2],
  "queryIntent": "FACT_QA",
  "confidence": 0.82,
  "reason": "问题涉及公司差旅制度"
}
```

字段含义：

- `action`：本轮处理动作，是主流程真正依赖的字段。
- `knowledgeBaseIds`：本轮允许检索的知识库 ID。
- `queryIntent`：用户问题意图，用于后续策略扩展和日志分析。
- `confidence`：Router 判断置信度，用于兜底。
- `reason`：Router 选择原因，用于日志排查和调试。

### action

```text
NO_KB
SEARCH_KB
REUSE_LAST_CONTEXT
```

- `NO_KB`：不查询知识库，适用于普通聊天、翻译、润色、格式转换等不依赖企业知识的请求。
- `SEARCH_KB`：查询知识库，适用于制度查询、文档事实问答、知识库内容问答。
- `REUSE_LAST_CONTEXT`：复用上一轮引用内容，适用于“总结一下”“换成表格”“刚才这个依据是什么”等请求。

本期暂不实现真正的上下文复用策略。若 Router 返回 `REUSE_LAST_CONTEXT`，后端统一降级为 `SEARCH_KB`，重新查询知识库。这样第一版行为更稳定，也更方便观察检索质量。

### queryIntent

```text
FACT_QA
SUMMARY
FORMAT_CONVERT
FOLLOW_UP
CHAT
```

- `FACT_QA`：事实问答，通常需要查询知识库。
- `SUMMARY`：总结类问题，后续可以复用上一轮引用内容。
- `FORMAT_CONVERT`：格式转换，后续可以复用上一轮回答或引用内容。
- `FOLLOW_UP`：追问，后续需要结合最近消息做 Query 改写。
- `CHAT`：普通聊天，通常不查知识库。

第一版不强依赖 `queryIntent` 做复杂分支，但需要记录该字段，方便后续优化查询策略。

### confidence

`confidence` 用于控制兜底策略。

第一版建议：

```text
confidence < 0.6，且 action 不是 SEARCH_KB 时，保守改为 SEARCH_KB。
```

原因是企业知识库场景下，漏查通常比多查更危险。

### reason

`reason` 不参与算法决策，主要用于：

- 日志排查。
- 前端调试展示。
- 分析 Router 为什么选择某个知识库。
- 后续优化 Router Prompt。

## 检索设计

### 本期检索方式

本期只做稠密向量检索。

```text
用户问题 -> DashScope text-embedding-v4 -> Milvus dense vector search
```

Milvus 使用当前已经存在的 `enterprise_kb_chunk` collection。

检索时使用 `knowledge_base_id` 过滤：

```text
knowledge_base_id in [2, 5]
```

这样可以在一个 collection 中承载多个知识库，不需要为每个知识库单独建 collection。

### 查询文本

第一版直接使用当前用户问题生成 embedding。

本期不做：

- Query 改写。
- HyDE 假设答案生成。
- 多 query 扩展。

### 返回数量

建议第一版参数：

```text
retrievalTopK = 10
contextTopK = 5
```

- `retrievalTopK`：Milvus 初步召回数量。
- `contextTopK`：最终送入大模型的 chunk 数量。

由于本期没有 Rerank，默认可以取 Milvus 返回的前 `contextTopK` 条进入 Prompt。

### 检索结果去重

本期只有一路 Dense Retrieval，理论上不需要复杂去重。

但仍建议按 `chunk_id` 做一次去重，避免后续引入多路检索时接口变化过大。

## 溯源设计

### Milvus 存储内容

Milvus 只存储检索必要字段：

- `id`
- `knowledge_base_id`
- `file_id`
- `chunk_id`
- `chunk_index`
- `embedding_source`
- `content_hash`
- `vector`

Milvus 不存储完整原文。

### MySQL 回查

Milvus 返回 `chunk_id` 后，后端批量查询 MySQL 的 chunk 元数据。

需要获取：

- `chunk_id`
- `knowledge_base_id`
- `file_id`
- `chunk_index`
- `title_path`
- `content_preview`
- `storage_bucket`
- `storage_object_key`

同时根据 `file_id` 查询文件信息：

- 文件名
- 文件类型
- 文件大小

### MinIO 读取

根据 chunk 元数据中的 `storage_bucket` 和 `storage_object_key` 读取原始 chunk 正文。

最终给大模型的上下文建议格式：

```text
[引用 1]
文件：xxx.docx
标题路径：制度汇编 / 差旅报销 / 住宿标准
分块序号：3
内容：
原始 chunk 正文...
```

返回给前端的引用来源建议包含：

- 引用序号
- 知识库 ID
- 文件 ID
- 文件名
- chunk ID
- chunk 序号
- 标题路径
- 命中分数
- 内容预览

## Prompt 设计

RAG 回答 Prompt 应明确约束：

- 只能基于提供的引用内容回答。
- 如果引用内容不足以回答，应明确说明“知识库中没有找到明确依据”。
- 不要使用外部知识补充企业制度事实。
- 回答中可以标注引用序号。
- 如果多个引用冲突，应说明存在不一致，不能自行合并成确定结论。

示例结构：

```text
你是企业知识库问答助手。
请严格基于【引用内容】回答【用户问题】。
如果引用内容无法回答，请说“知识库中没有找到明确依据”。
不要编造制度、金额、日期、流程或审批规则。

【引用内容】
...

【用户问题】
...
```

## 会话设计

### 会话隔离

每个聊天窗口拥有独立的 `conversationId`。

不同 `conversationId` 的消息、引用记录、后续上下文策略互相隔离。

### 消息保存

需要保存：

- 用户消息。
- 助手消息。
- 消息顺序。
- 消息创建时间。
- 对应的 Router 结果。
- 对应的引用来源。

### 上下文压缩

本期不做上下文压缩。

表结构和应用服务可以不为压缩做复杂实现，只需要避免把设计写死，后续可以新增会话摘要表或上下文字段。

## 数据模型建议

### conversation

保存聊天窗口。

建议字段：

- `id`
- `title`
- `created_at`
- `updated_at`

### conversation_message

保存聊天消息。

建议字段：

- `id`
- `conversation_id`
- `role`
- `content`
- `message_order`
- `created_at`
- `updated_at`

`role` 建议：

```text
USER
ASSISTANT
SYSTEM
```

### conversation_retrieval

保存每次助手回答对应的检索记录。

建议字段：

- `id`
- `conversation_id`
- `message_id`
- `query_text`
- `action`
- `knowledge_base_ids_json`
- `query_intent`
- `confidence`
- `reason`
- `created_at`
- `updated_at`

### conversation_retrieval_reference

保存每次回答引用了哪些 chunk。

建议字段：

- `id`
- `conversation_retrieval_id`
- `knowledge_base_id`
- `file_id`
- `chunk_id`
- `chunk_index`
- `title_path`
- `score`
- `content_preview`
- `created_at`
- `updated_at`

引用表不建议保存完整 chunk 正文，完整正文仍以 MinIO 中的 chunk 文本为准。

## API 设计建议

### 创建会话

```text
POST /api/conversations
```

返回：

```json
{
  "id": 1,
  "title": "新会话"
}
```

### 查询会话列表

```text
GET /api/conversations
```

### 查询会话消息

```text
GET /api/conversations/{conversationId}/messages
```

### 发送消息

```text
POST /api/conversations/{conversationId}/messages
```

请求：

```json
{
  "content": "西安出差住宿能报销多少？"
}
```

响应：

```json
{
  "messageId": 20,
  "content": "根据引用 1，...",
  "router": {
    "action": "SEARCH_KB",
    "knowledgeBaseIds": [2],
    "queryIntent": "FACT_QA",
    "confidence": 0.82,
    "reason": "问题涉及公司差旅制度"
  },
  "references": [
    {
      "referenceNo": 1,
      "knowledgeBaseId": 2,
      "fileId": 18,
      "fileName": "差旅制度.docx",
      "chunkId": 537,
      "chunkIndex": 0,
      "titlePath": "制度汇编 / 差旅报销 / 住宿标准",
      "score": 0.83,
      "contentPreview": "非一线城市住宿补贴上限为..."
    }
  ]
}
```

## 前端设计

第一版前端建议新增一个查询页面：

- 左侧会话列表。
- 中间消息区域。
- 底部输入框。
- 助手消息下方展示引用来源。
- 引用来源可展开查看文件名、标题路径、chunk 序号、内容预览。

本期不做：

- 流式输出。
- 引用原文全文弹窗。
- 手动选择知识库。
- 高级检索参数配置。

## 异常处理

### Router 失败

如果 Router 调用失败，建议保守降级：

```text
action = SEARCH_KB
knowledgeBaseIds = 所有启用知识库
reason = Router 调用失败，降级查询全部启用知识库
confidence = 0
```

如果当前没有任何启用知识库，则走普通回答，并提示无法查询知识库。

### Embedding 失败

返回明确错误：

```text
问题向量生成失败，请稍后重试。
```

并记录完整异常堆栈。

### Milvus 检索失败

返回明确错误：

```text
知识库检索失败，请稍后重试。
```

并记录完整异常堆栈。

### 未检索到内容

如果没有召回任何 chunk，大模型不应编造答案。

可以直接返回：

```text
知识库中没有找到与该问题相关的内容。
```

### 大模型回答失败

如果检索成功但回答生成失败，应返回明确错误，并保留本轮检索记录，便于排查。

## 日志要求

后端需要记录：

- Router 入参和出参。
- Router 失败异常堆栈。
- Embedding 请求文本数量和模型名。
- Milvus 查询过滤条件、TopK、命中数量。
- MySQL 回查 chunk 数量。
- MinIO 读取 chunk 数量。
- 大模型生成失败异常堆栈。
- 最终回答使用的引用数量。

日志不得输出 API Key。

## 后续迭代

### 第二期：Query 改写

结合最近几轮消息，将追问补全为独立问题。

示例：

```text
上一轮：差旅住宿标准是多少？
本轮：那西安呢？
改写：西安出差住宿报销标准是多少？
```

### 第三期：HyDE

让大模型基于问题生成一段假设答案，再用假设答案做 embedding 检索。

适合开放式、解释型问题。

需要注意：HyDE 生成内容不能作为事实依据，只能作为检索辅助。

### 第四期：混合检索

引入关键词检索和 BM25。

Milvus 支持 full text search 和 BM25，但需要调整 collection schema，增加文本字段、sparse vector 字段和 BM25 function。

混合检索建议：

```text
Dense Retrieval + Sparse Retrieval -> RRF 融合 -> 去重 -> TopK
```

### 第五期：Rerank

在多路召回之后接入 Reranker。

推荐顺序：

```text
多路召回 Top 30 -> Cross-Encoder Rerank -> Top 5 -> LLM
```

也可以先做规则加权：

- 标题路径命中加分。
- 文件名命中加分。
- 知识库优先级加分。

### 第六期：上下文压缩

当会话变长后，对旧消息生成摘要。

摘要只用于理解追问，不作为事实依据。

### 第七期：评估与调优

引入自动化评估数据集，观察：

- Context Precision
- Context Recall
- Faithfulness
- Answer Relevancy

根据 bad case 调整 chunk 策略、embedding、检索参数和 Prompt。
