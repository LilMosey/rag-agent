# RAG Rerank 重排序设计

## 背景

当前 RAG 查询链路已经支持：

- Router 自动判断是否查询知识库。
- Query 改写。
- HyDE。
- 多 Query。
- Dense 检索。
- BM25 检索。
- RRF 融合。
- 会话上下文复用。
- `USE_PREVIOUS_AND_SEARCH` 复用上一轮引用并补查新内容。

现有链路的召回能力已经比较完整，但 RRF 融合后的排序仍然属于粗排。Dense、BM25、HyDE、多 Query 都能把候选 chunk 找回来，但不能保证最终送入大模型的 TopK 一定最贴合用户问题。

Rerank 的目标是在召回候选之后进行精排，减少“语义相关但答非所问”的 chunk 进入最终上下文。

## 目标

- 接入 DashScope Java SDK 的 `TextReRank`。
- 默认使用模型 `gte-rerank-v2`。
- 在 RRF 融合之后、最终上下文截断之前执行 Rerank。
- Rerank 输入使用当前检索问题和候选 chunk 原文。
- Rerank 输出按 `relevance_score` 排序。
- Rerank 失败时降级使用 RRF 结果，不影响回答。
- Rerank 过程可配置开启或关闭。
- Rerank 候选数量可配置。
- Rerank 结果保存到现有检索任务表，便于排查。
- 第一版不在前端展示 Rerank 内部细节。

## 非目标

- 不做 RAGAS 评估。
- 不做上下文压缩。
- 不做长期记忆。
- 不做前端 Rerank 调试页。
- 不新增数据库表。
- 不更改 Milvus collection schema。
- 不做 Java 单元测试。

## 技术选型

### 推荐实现

使用本地已引入的 DashScope SDK：

```java
import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankParam;
import com.alibaba.dashscope.rerank.TextReRankResult;
```

模型：

```text
gte-rerank-v2
```

本地 SDK 源码中 `TextReRank.Models` 已提供：

```java
TextReRank.Models.GTE_RERANK_V2
```

### 为什么不用 LLM 打分

LLM 可以通过 Prompt 对 chunk 相关性打分，但第一版不推荐：

- 输出格式稳定性弱。
- 成本更高。
- 延迟更高。
- 批量候选排序不如专用 Rerank 模型直接。

Rerank 模型本身就是为 query-document 相关性排序准备的，更适合放在检索链路中。

## 总体链路

```text
用户问题
  -> Router
  -> Query 改写 / HyDE / 多 Query / BM25
  -> 并发检索
  -> RRF 融合
  -> 截取 rerankCandidateTopK
  -> 回查 chunk 原文
  -> DashScope TextReRank
  -> 按 relevance_score 排序
  -> 截取 contextTopK
  -> 组装引用上下文
  -> 调用大模型回答
```

`USE_PREVIOUS_AND_SEARCH` 场景：

```text
上一轮引用候选
  + 新检索 RRF 结果
  -> 去重合并
  -> Rerank
  -> contextTopK
  -> 回答
```

## 配置设计

新增配置：

```yaml
rag:
  retrieval:
    rerank-enabled: true
    rerank-model: gte-rerank-v2
    rerank-api-key:
    rerank-base-url:
    rerank-candidate-top-k: 30
    rerank-return-documents: false
```

配置含义：

- `rerank-enabled`：是否启用 Rerank。
- `rerank-model`：Rerank 模型名称。
- `rerank-api-key`：DashScope API Key，可在 `application-test.yml` 或环境变量映射中配置，默认不写入主配置。
- `rerank-base-url`：DashScope 原生 HTTP API 地址，可为空；为空时使用 SDK 默认地址。
- `rerank-candidate-top-k`：RRF 之后进入 Rerank 的候选数量。
- `rerank-return-documents`：是否要求 DashScope 返回原文。第一版不需要返回原文，因为本地已经有 chunk 映射。

默认值建议：

```text
rerank-enabled = true
rerank-model = gte-rerank-v2
rerank-api-key = 空
rerank-base-url = 空
rerank-candidate-top-k = 30
rerank-return-documents = false
```

## 应用层接口

新增端口：

```java
public interface RerankGenerator {

    RerankResult rerank(RerankCommand command);

    record RerankCommand(
            String query,
            List<RerankDocument> documents,
            Integer topN
    ) {
    }

    record RerankDocument(
            Long chunkId,
            String content
    ) {
    }

    record RerankResult(
            List<RerankItem> items,
            String provider,
            String model
    ) {
    }

    record RerankItem(
            Long chunkId,
            BigDecimal score,
            Integer rankNo
    ) {
    }
}
```

端口放在 application 层，DashScope 实现放在 infrastructure 层。

这样后续如果切换其他 Rerank 模型，不需要改 `RagRetrievalService` 或 `RagChatService` 的核心逻辑。

## DashScope 实现

实现类建议：

```text
backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/rag/DashScopeRerankGenerator.java
```

调用方式：

```java
if (rerankApiKey != null && !rerankApiKey.isBlank()) {
    Constants.apiKey = rerankApiKey;
}
if (baseUrl != null && !baseUrl.isBlank()) {
    Constants.baseHttpApiUrl = baseUrl;
}
TextReRankParam param = TextReRankParam.builder()
        .model(model)
        .query(query)
        .documents(documents)
        .topN(topN)
        .returnDocuments(false)
        .build();
TextReRankResult result = textReRank.call(param);
```

DashScope 返回的 `index` 是候选 documents 的原始下标。实现层需要用 `index -> chunkId` 映射回业务 chunk。

## Rerank 放在哪里

推荐放在 `RagChatService` 中，理由：

- `RagRetrievalService` 当前只返回 `SearchHit`，还没有 chunk 原文。
- Rerank 需要 chunk 原文。
- `RagChatService` 当前已经负责 `hydrateReferences`，能拿到 MinIO 中的 chunk 原文。
- 放在 `RagChatService` 中可以少改现有检索服务边界。

目标结构：

```text
RagRetrievalService.retrieve()
  -> 返回 fusedHits

RagChatService.searchReferences()
  -> 截取 rerankCandidateTopK
  -> hydrateReferences()
  -> rerankReferenceCandidates()
  -> 截取 contextTopK
```

注意：当前 `searchReferences()` 已经在 `hydrateReferences()` 前截取 `contextTopK`。实现 Rerank 后需要改成：

```text
先按 rerankCandidateTopK 截取
再 hydrateReferences
再 rerank
最后按 contextTopK 截取
```

## 结果保存

新增枚举：

```java
RERANK
```

加入：

```text
backend/kb-domain/src/main/java/com/example/kb/domain/model/RetrievalTaskType.java
```

不新增表，复用：

- `conversation_retrieval_task`
- `conversation_retrieval_task_hit`

Rerank 成功时保存：

```text
conversation_retrieval_task.task_type = RERANK
conversation_retrieval_task.query_text = 当前用于 Rerank 的 query
conversation_retrieval_task.status = SUCCESS
conversation_retrieval_task_hit.score = relevance_score
conversation_retrieval_task_hit.rank_no = rerank 后排名
```

Rerank 失败时保存：

```text
task_type = RERANK
status = FAILED
error_message = 失败原因
hit 不保存
```

Rerank 关闭时不保存 `RERANK` 任务。

## 降级策略

Rerank 降级规则：

1. `rerank-enabled=false`：跳过 Rerank，使用 RRF 排序。
2. 候选为空：跳过 Rerank。
3. DashScope 调用异常：记录失败任务，使用 RRF 排序。
4. DashScope 返回为空：记录失败任务，使用 RRF 排序。
5. DashScope 返回的 index 越界：跳过该条异常 item，继续处理其他 item。
6. Rerank 有部分合法结果：合法结果排前，未返回的候选保持原 RRF 顺序追加在后。

这样可以保证 Rerank 永远不会阻断回答链路。

## 日志要求

Rerank 每个关键分支都要打印日志：

- 入参：query 长度、候选数量、topN、model。
- 出参：返回数量、耗时、provider、model。
- 失败：异常堆栈、query 长度、候选数量。
- 降级：说明使用 RRF 原始排序。
- 保存任务：task 类型、状态、命中数量。

## 验证方式

### 编译验证

```bash
cd /Users/tangjie/javaai/agent/backend
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

### 约束扫描

```bash
cd /Users/tangjie/javaai/agent
rg "\bvar\b" backend --glob "*.java"
rg "@Insert|@Update|@Delete|@Select" backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/mapper
```

预期：

- 第一条没有 Java `var` 命中。
- 第二条没有 Mapper 注解 SQL 命中。

### 功能验证

1. 启动后端。
2. 确认 DashScope API Key 可用。
3. 确认已有知识库文件处于 READY。
4. 发起知识库查询。
5. 观察日志中出现 Rerank 入参和出参。
6. 查询数据库确认有 `RERANK` 任务记录。

示例 SQL：

```sql
SELECT t.*
FROM conversation_retrieval_task t
WHERE t.task_type = 'RERANK'
ORDER BY t.id DESC;
```

```sql
SELECT h.*
FROM conversation_retrieval_task_hit h
JOIN conversation_retrieval_task t ON h.retrieval_task_id = t.id
WHERE t.task_type = 'RERANK'
ORDER BY h.rank_no;
```

## 后续扩展

后续可以继续做：

- 前端展示 Rerank 分数。
- Rerank 前后排序对比。
- RAGAS 评估。
- 基于 bad case 调整 `rerank-candidate-top-k`。
- 按知识库或会话配置是否启用 Rerank。
