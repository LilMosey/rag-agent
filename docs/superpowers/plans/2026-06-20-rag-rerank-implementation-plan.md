# RAG Rerank 重排序 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 RAG 检索链路中接入 DashScope `TextReRank`，将 RRF 融合后的候选 chunk 精排后再进入最终回答上下文。

**Architecture:** 新增 application 层 `RerankGenerator` 端口，infrastructure 层使用 DashScope SDK `TextReRank` 实现。Rerank 放在 `RagChatService` 的引用组装阶段：先取 RRF TopN 候选并回查 chunk 原文，再调用 Rerank，最后取 `contextTopK` 给大模型。

**Tech Stack:** Java、Spring Boot、DashScope SDK `com.alibaba.dashscope.rerank.TextReRank`、MyBatis XML、MySQL、Milvus、MinIO。

---

## 文件结构

- Modify: `backend/kb-domain/src/main/java/com/example/kb/domain/model/RetrievalTaskType.java`
  - 新增 `RERANK` 枚举值。

- Create: `backend/kb-application/src/main/java/com/example/kb/application/port/RerankGenerator.java`
  - 定义 Rerank 端口、命令、候选文档和结果对象。

- Modify: `backend/kb-application/src/main/java/com/example/kb/application/service/RagRetrievalProperties.java`
  - 增加 Rerank 配置字段和安全默认值。

- Modify: `backend/kb-application/src/main/java/com/example/kb/application/service/RagChatService.java`
  - 注入 `RerankGenerator` 和 `RagRetrievalProperties`。
  - 将 `searchReferences()` 改为先取 Rerank 候选，再回查正文，再 Rerank，最后截取 `contextTopK`。
  - 为 Rerank 成功或失败生成 `RetrievalTaskReport`。

- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/rag/DashScopeRerankGenerator.java`
  - 使用 DashScope `TextReRank` 调用 `gte-rerank-v2`。
  - 将返回的 `index` 映射回 `chunkId`。

- Modify: `backend/kb-bootstrap/src/main/java/com/example/kb/bootstrap/ApplicationServiceConfiguration.java`
  - 从环境变量读取 Rerank 配置。
  - 注册 `RerankGenerator` Bean。
  - 构造 `RagChatService` 时传入新依赖。

- Modify: `backend/kb-bootstrap/src/main/resources/application.yml`
  - 新增 Rerank 默认配置。

## Task 1: 扩展检索任务类型

**Files:**
- Modify: `backend/kb-domain/src/main/java/com/example/kb/domain/model/RetrievalTaskType.java`

- [ ] **Step 1: 打开枚举文件**

确认当前枚举为：

```java
public enum RetrievalTaskType {
    ORIGINAL_DENSE,
    REWRITE_DENSE,
    HYDE_DENSE,
    MULTI_QUERY_DENSE,
    BM25
}
```

- [ ] **Step 2: 新增 RERANK**

修改为：

```java
public enum RetrievalTaskType {
    ORIGINAL_DENSE,
    REWRITE_DENSE,
    HYDE_DENSE,
    MULTI_QUERY_DENSE,
    BM25,
    RERANK
}
```

- [ ] **Step 3: 编译验证**

Run:

```bash
cd /Users/tangjie/javaai/agent/backend
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected: 编译通过。

## Task 2: 新增 RerankGenerator 端口

**Files:**
- Create: `backend/kb-application/src/main/java/com/example/kb/application/port/RerankGenerator.java`

- [ ] **Step 1: 创建端口文件**

新增完整内容：

```java
package com.example.kb.application.port;

import java.math.BigDecimal;
import java.util.List;

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

- [ ] **Step 2: 编译验证**

Run:

```bash
cd /Users/tangjie/javaai/agent/backend
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected: 编译通过。

## Task 3: 扩展 RAG 检索配置

**Files:**
- Modify: `backend/kb-application/src/main/java/com/example/kb/application/service/RagRetrievalProperties.java`
- Modify: `backend/kb-bootstrap/src/main/java/com/example/kb/bootstrap/ApplicationServiceConfiguration.java`
- Modify: `backend/kb-bootstrap/src/main/resources/application.yml`

- [ ] **Step 1: 修改 RagRetrievalProperties record 字段**

在 `bm25Enabled` 后加入：

```java
Boolean rerankEnabled,
String rerankModel,
String rerankApiKey,
String rerankBaseUrl,
Integer rerankCandidateTopK,
Boolean rerankReturnDocuments,
```

record 字段顺序建议为：

```java
public record RagRetrievalProperties(
        Boolean queryRewriteEnabled,
        Boolean hydeEnabled,
        Boolean multiQueryEnabled,
        Boolean bm25Enabled,
        Boolean rerankEnabled,
        String rerankModel,
        String rerankApiKey,
        String rerankBaseUrl,
        Integer rerankCandidateTopK,
        Boolean rerankReturnDocuments,
        Integer queryRewriteHistoryMessageLimit,
        Integer multiQueryCount,
        Integer denseTopK,
        Integer bm25TopK,
        Integer fusionTopK,
        Integer contextTopK,
        Integer rrfK,
        Integer executorCoreSize,
        Integer executorMaxSize,
        Integer executorQueueCapacity
)
```

- [ ] **Step 2: 添加安全默认值方法**

在 `RagRetrievalProperties` 中新增：

```java
public boolean isRerankEnabled() {
    return rerankEnabled != null && rerankEnabled;
}

public String safeRerankModel() {
    if (rerankModel == null || rerankModel.isBlank()) {
        return "gte-rerank-v2";
    }
    return rerankModel;
}

public String safeRerankApiKey() {
    if (rerankApiKey == null || rerankApiKey.isBlank()) {
        return "";
    }
    return rerankApiKey;
}

public String safeRerankBaseUrl() {
    if (rerankBaseUrl == null || rerankBaseUrl.isBlank()) {
        return "";
    }
    return rerankBaseUrl;
}

public int safeRerankCandidateTopK() {
    return rerankCandidateTopK == null ? 30 : rerankCandidateTopK;
}

public boolean isRerankReturnDocuments() {
    return rerankReturnDocuments != null && rerankReturnDocuments;
}
```

- [ ] **Step 3: 修改 ApplicationServiceConfiguration 读取配置**

在 `ragRetrievalProperties(Environment environment)` 构造参数中，在 `bm25-enabled` 后加入：

```java
environment.getProperty("rag.retrieval.rerank-enabled", Boolean.class),
environment.getProperty("rag.retrieval.rerank-model", String.class),
environment.getProperty("rag.retrieval.rerank-api-key", String.class),
environment.getProperty("rag.retrieval.rerank-base-url", String.class),
environment.getProperty("rag.retrieval.rerank-candidate-top-k", Integer.class),
environment.getProperty("rag.retrieval.rerank-return-documents", Boolean.class),
```

- [ ] **Step 4: 修改 application.yml**

在 `rag.retrieval` 下加入：

```yaml
    rerank-enabled: true
    rerank-model: gte-rerank-v2
    rerank-api-key:
    rerank-base-url:
    rerank-candidate-top-k: 30
    rerank-return-documents: false
```

- [ ] **Step 5: 编译验证**

Run:

```bash
cd /Users/tangjie/javaai/agent/backend
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected: 编译通过。

## Task 4: 实现 DashScopeRerankGenerator

**Files:**
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/rag/DashScopeRerankGenerator.java`

- [ ] **Step 1: 创建实现类**

新增完整内容：

```java
package com.example.kb.infrastructure.rag;

import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankOutput;
import com.alibaba.dashscope.rerank.TextReRankParam;
import com.alibaba.dashscope.rerank.TextReRankResult;
import com.alibaba.dashscope.utils.Constants;
import com.example.kb.application.port.RerankGenerator;
import com.example.kb.application.service.RagRetrievalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DashScopeRerankGenerator implements RerankGenerator {

    private static final Logger log = LoggerFactory.getLogger(DashScopeRerankGenerator.class);

    private final RagRetrievalProperties ragRetrievalProperties;
    private final TextReRank textReRank;

    public DashScopeRerankGenerator(RagRetrievalProperties ragRetrievalProperties) {
        this.ragRetrievalProperties = ragRetrievalProperties;
        if (!ragRetrievalProperties.safeRerankApiKey().isBlank()) {
            Constants.apiKey = ragRetrievalProperties.safeRerankApiKey();
        }
        if (!ragRetrievalProperties.safeRerankBaseUrl().isBlank()) {
            Constants.baseHttpApiUrl = ragRetrievalProperties.safeRerankBaseUrl();
        }
        this.textReRank = new TextReRank();
    }

    @Override
    public RerankResult rerank(RerankCommand command) {
        log.info("DashScope Rerank 入参: queryLength={}, documentCount={}, topN={}, model={}",
                command.query().length(), command.documents().size(), command.topN(),
                ragRetrievalProperties.safeRerankModel());
        try {
            List<String> documents = command.documents().stream()
                    .map(RerankDocument::content)
                    .toList();
            TextReRankParam param = TextReRankParam.builder()
                    .apiKey(ragRetrievalProperties.safeRerankApiKey())
                    .model(ragRetrievalProperties.safeRerankModel())
                    .query(command.query())
                    .documents(documents)
                    .topN(command.topN())
                    .returnDocuments(ragRetrievalProperties.isRerankReturnDocuments())
                    .build();
            TextReRankResult result = textReRank.call(param);
            List<RerankItem> items = toRerankItems(command.documents(), result);
            log.info("DashScope Rerank 出参: itemCount={}, provider={}, model={}",
                    items.size(), "dashscope", ragRetrievalProperties.safeRerankModel());
            return new RerankResult(items, "dashscope", ragRetrievalProperties.safeRerankModel());
        } catch (Exception exception) {
            log.error("DashScope Rerank 异常: queryLength={}, documentCount={}",
                    command.query().length(), command.documents().size(), exception);
            throw new IllegalStateException("DashScope Rerank 失败: " + exception.getMessage(), exception);
        }
    }

    private List<RerankItem> toRerankItems(
            List<RerankDocument> documents,
            TextReRankResult result
    ) {
        if (result == null || result.getOutput() == null || result.getOutput().getResults() == null) {
            log.warn("DashScope Rerank 分支: 返回结果为空");
            return List.of();
        }
        List<RerankItem> items = new ArrayList<>();
        int rankNo = 1;
        for (TextReRankOutput.Result item : result.getOutput().getResults()) {
            if (item.getIndex() == null || item.getIndex() < 0 || item.getIndex() >= documents.size()) {
                log.warn("DashScope Rerank 分支: 跳过非法 index, index={}, documentCount={}",
                        item.getIndex(), documents.size());
                continue;
            }
            RerankDocument document = documents.get(item.getIndex());
            BigDecimal score = item.getRelevanceScore() == null
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(item.getRelevanceScore());
            items.add(new RerankItem(document.chunkId(), score, rankNo));
            rankNo++;
        }
        return items;
    }
}
```

- [ ] **Step 2: 检查 Lombok getter 名称**

Run:

```bash
cd /Users/tangjie/javaai/agent/backend
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected: 如果 `TextReRankResult` 或 `TextReRankOutput.Result` getter 名称和 SDK 实际不一致，编译会报错；按本地 sources.jar 中的 Lombok `@Data` getter 调整。

## Task 5: 注册 RerankGenerator Bean

**Files:**
- Modify: `backend/kb-bootstrap/src/main/java/com/example/kb/bootstrap/ApplicationServiceConfiguration.java`

- [ ] **Step 1: 增加 import**

加入：

```java
import com.example.kb.application.port.RerankGenerator;
import com.example.kb.infrastructure.rag.DashScopeRerankGenerator;
```

- [ ] **Step 2: 新增 Bean**

在 `multiQueryGenerator` 或 `rrfFusionService` 附近加入：

```java
@Bean
public RerankGenerator rerankGenerator(
        RagProperties ragProperties,
        RagRetrievalProperties ragRetrievalProperties
) {
    log.info("Rerank 生成器分支: 使用 DashScope, provider={}, model={}, enabled={}",
            ragProperties.provider(), ragRetrievalProperties.safeRerankModel(),
            ragRetrievalProperties.isRerankEnabled());
    return new DashScopeRerankGenerator(ragRetrievalProperties);
}
```

- [ ] **Step 3: 编译验证**

Run:

```bash
cd /Users/tangjie/javaai/agent/backend
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected: 编译通过。

## Task 6: 改造 RagChatService 执行 Rerank

**Files:**
- Modify: `backend/kb-application/src/main/java/com/example/kb/application/service/RagChatService.java`

- [ ] **Step 1: 增加依赖字段**

新增 import：

```java
import com.example.kb.application.port.RerankGenerator;
import com.example.kb.domain.model.RetrievalTaskType;
```

新增字段：

```java
private final RerankGenerator rerankGenerator;
private final RagRetrievalProperties ragRetrievalProperties;
```

构造器参数中加入：

```java
RerankGenerator rerankGenerator,
RagRetrievalProperties ragRetrievalProperties,
```

构造器内赋值：

```java
this.rerankGenerator = rerankGenerator;
this.ragRetrievalProperties = ragRetrievalProperties;
```

- [ ] **Step 2: 修改 searchReferences 的候选截断逻辑**

将当前：

```java
List<VectorIndexSearcher.SearchHit> selectedHits = retrievalResult.fusedHits().stream()
        .limit(contextTopK)
        .toList();
List<ReferenceCandidate> referenceCandidates = hydrateReferences(selectedHits);
```

改为：

```java
List<VectorIndexSearcher.SearchHit> selectedHits = retrievalResult.fusedHits().stream()
        .limit(ragRetrievalProperties.safeRerankCandidateTopK())
        .toList();
List<ReferenceCandidate> hydratedCandidates = hydrateReferences(selectedHits);
RerankReferenceResult rerankReferenceResult = rerankReferenceCandidates(searchText, hydratedCandidates);
List<ReferenceCandidate> referenceCandidates = rerankReferenceResult.referenceCandidates().stream()
        .limit(contextTopK)
        .toList();
List<RagRetrievalService.RetrievalTaskReport> taskReports = new ArrayList<>(retrievalResult.taskReports());
rerankReferenceResult.taskReport().ifPresent(taskReports::add);
```

并把返回值改为：

```java
return new SearchReferenceResult(referenceCandidates, taskReports);
```

- [ ] **Step 3: 新增 rerankReferenceCandidates 方法**

在 `hydrateReferences` 附近新增：

```java
private RerankReferenceResult rerankReferenceCandidates(
        String query,
        List<ReferenceCandidate> candidates
) {
    log.info("Rerank 引用候选入参: enabled={}, queryLength={}, candidateCount={}",
            ragRetrievalProperties.isRerankEnabled(), query.length(), candidates.size());
    if (!ragRetrievalProperties.isRerankEnabled()) {
        log.info("Rerank 引用候选分支: 配置关闭，使用 RRF 排序");
        return new RerankReferenceResult(candidates, Optional.empty());
    }
    if (candidates.isEmpty()) {
        log.info("Rerank 引用候选分支: 候选为空");
        return new RerankReferenceResult(candidates, Optional.empty());
    }
    LocalDateTime startedAt = LocalDateTime.now();
    try {
        List<RerankGenerator.RerankDocument> documents = new ArrayList<>(candidates.size());
        for (ReferenceCandidate candidate : candidates) {
            documents.add(new RerankGenerator.RerankDocument(candidate.chunk().id(), candidate.content()));
        }
        RerankGenerator.RerankResult rerankResult = rerankGenerator.rerank(
                new RerankGenerator.RerankCommand(
                        query,
                        documents,
                        Math.min(contextTopK, candidates.size())
                )
        );
        List<ReferenceCandidate> rerankedCandidates = applyRerankResult(candidates, rerankResult.items());
        RagRetrievalService.RetrievalTaskReport taskReport = RagRetrievalService.RetrievalTaskReport.success(
                RagRetrievalService.RetrievalTaskDraft.running(RetrievalTaskType.RERANK, query),
                toSearchHits(rerankedCandidates),
                startedAt,
                LocalDateTime.now()
        );
        log.info("Rerank 引用候选出参: inputCount={}, outputCount={}",
                candidates.size(), rerankedCandidates.size());
        return new RerankReferenceResult(rerankedCandidates, Optional.of(taskReport));
    } catch (Exception exception) {
        log.error("Rerank 引用候选异常: queryLength={}, candidateCount={}",
                query.length(), candidates.size(), exception);
        RagRetrievalService.RetrievalTaskReport taskReport = RagRetrievalService.RetrievalTaskReport.failed(
                RagRetrievalService.RetrievalTaskDraft.failed(RetrievalTaskType.RERANK, query, exception.getMessage()),
                startedAt,
                LocalDateTime.now()
        );
        return new RerankReferenceResult(candidates, Optional.of(taskReport));
    }
}
```

- [ ] **Step 4: 调整 RetrievalTaskDraft 可见性**

如果 `RagRetrievalService.RetrievalTaskDraft` 当前是 `private record`，需要改成 `public record`，并保留静态工厂方法：

```java
public record RetrievalTaskDraft(
        RetrievalTaskType taskType,
        String queryText,
        RetrievalTaskStatus status,
        String errorMessage
) {
    public static RetrievalTaskDraft running(RetrievalTaskType taskType, String queryText) {
        return new RetrievalTaskDraft(taskType, queryText, RetrievalTaskStatus.RUNNING, null);
    }

    public static RetrievalTaskDraft skipped(RetrievalTaskType taskType, String queryText, String errorMessage) {
        return new RetrievalTaskDraft(taskType, queryText, RetrievalTaskStatus.SKIPPED, errorMessage);
    }

    public static RetrievalTaskDraft failed(RetrievalTaskType taskType, String queryText, String errorMessage) {
        return new RetrievalTaskDraft(taskType, queryText, RetrievalTaskStatus.FAILED, errorMessage);
    }
}
```

- [ ] **Step 5: 新增 applyRerankResult 方法**

在 `RagChatService` 中新增：

```java
private List<ReferenceCandidate> applyRerankResult(
        List<ReferenceCandidate> candidates,
        List<RerankGenerator.RerankItem> rerankItems
) {
    if (rerankItems == null || rerankItems.isEmpty()) {
        log.warn("应用 Rerank 结果分支: rerankItems 为空，使用原排序");
        return candidates;
    }
    Map<Long, ReferenceCandidate> candidateMap = new LinkedHashMap<>();
    for (ReferenceCandidate candidate : candidates) {
        candidateMap.put(candidate.chunk().id(), candidate);
    }
    List<ReferenceCandidate> rerankedCandidates = new ArrayList<>();
    for (RerankGenerator.RerankItem item : rerankItems) {
        ReferenceCandidate candidate = candidateMap.remove(item.chunkId());
        if (candidate == null) {
            log.warn("应用 Rerank 结果分支: chunkId 未命中候选, chunkId={}", item.chunkId());
            continue;
        }
        VectorIndexSearcher.SearchHit rerankHit = new VectorIndexSearcher.SearchHit(
                candidate.hit().knowledgeBaseId(),
                candidate.hit().fileId(),
                candidate.hit().chunkId(),
                candidate.hit().chunkIndex(),
                item.score()
        );
        rerankedCandidates.add(new ReferenceCandidate(
                item.rankNo(),
                rerankHit,
                candidate.chunk(),
                candidate.file(),
                candidate.content()
        ));
    }
    for (ReferenceCandidate candidate : candidateMap.values()) {
        rerankedCandidates.add(candidate);
    }
    return renumberReferenceCandidates(rerankedCandidates);
}
```

- [ ] **Step 6: 新增 toSearchHits 和 RerankReferenceResult**

新增方法：

```java
private List<VectorIndexSearcher.SearchHit> toSearchHits(List<ReferenceCandidate> candidates) {
    List<VectorIndexSearcher.SearchHit> hits = new ArrayList<>(candidates.size());
    for (ReferenceCandidate candidate : candidates) {
        hits.add(candidate.hit());
    }
    return hits;
}
```

新增 record：

```java
private record RerankReferenceResult(
        List<ReferenceCandidate> referenceCandidates,
        Optional<RagRetrievalService.RetrievalTaskReport> taskReport
) {
}
```

- [ ] **Step 7: 编译验证**

Run:

```bash
cd /Users/tangjie/javaai/agent/backend
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected: 编译通过。

## Task 7: 调整 ApplicationServiceConfiguration 构造 RagChatService

**Files:**
- Modify: `backend/kb-bootstrap/src/main/java/com/example/kb/bootstrap/ApplicationServiceConfiguration.java`

- [ ] **Step 1: 修改 ragChatService Bean 参数**

在 `ragChatService(...)` Bean 方法参数中加入：

```java
RerankGenerator rerankGenerator,
RagRetrievalProperties ragRetrievalProperties,
```

- [ ] **Step 2: 修改 RagChatService 构造调用**

在 `new RagChatService(...)` 中加入：

```java
rerankGenerator,
ragRetrievalProperties,
```

具体位置与 `ragRetrievalService`、`ragConversationContextService` 邻近，保持依赖顺序清晰。

- [ ] **Step 3: 编译验证**

Run:

```bash
cd /Users/tangjie/javaai/agent/backend
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected: 编译通过。

## Task 8: 运行约束扫描

**Files:**
- No file changes.

- [ ] **Step 1: 扫描 Java var**

Run:

```bash
cd /Users/tangjie/javaai/agent
rg "\bvar\b" backend --glob "*.java"
```

Expected: 无输出。

- [ ] **Step 2: 扫描 Mapper 注解 SQL**

Run:

```bash
cd /Users/tangjie/javaai/agent
rg "@Insert|@Update|@Delete|@Select" backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/mapper
```

Expected: 无输出。

## Task 9: 功能验证

**Files:**
- No file changes.

- [ ] **Step 1: 启动依赖服务**

用户在项目根目录执行：

```bash
cd /Users/tangjie/javaai/agent
docker compose up -d
```

Expected: MySQL、MinIO、Milvus、Attu 正常启动。

- [ ] **Step 2: 启动后端**

用户按当前项目已有方式启动 Spring Boot 后端。

Expected: 日志出现：

```text
Rerank 生成器分支: 使用 DashScope
```

- [ ] **Step 3: 发起一次知识库查询**

在前端 RAG 查询页面问一个需要知识库的问题。

Expected:

- Router action 是 `SEARCH_KB` 或 `USE_PREVIOUS_AND_SEARCH`。
- 后端日志出现 `Rerank 引用候选入参`。
- 后端日志出现 `DashScope Rerank 出参`。
- 回答正常生成。

- [ ] **Step 4: 查询 RERANK 任务**

在 DBeaver 执行：

```sql
SELECT t.*
FROM conversation_retrieval_task t
WHERE t.task_type = 'RERANK'
ORDER BY t.id DESC;
```

Expected: 至少有一条 `SUCCESS` 或 `FAILED` 记录。

- [ ] **Step 5: 查询 RERANK 命中**

在 DBeaver 执行：

```sql
SELECT h.*
FROM conversation_retrieval_task_hit h
JOIN conversation_retrieval_task t ON h.retrieval_task_id = t.id
WHERE t.task_type = 'RERANK'
ORDER BY h.rank_no;
```

Expected:

- 如果 Rerank 成功，有命中记录。
- `score` 是 Rerank relevance score。
- `rank_no` 是 Rerank 后排名。

## Task 10: 降级验证

**Files:**
- Modify temporarily: `backend/kb-bootstrap/src/main/resources/application.yml`

- [ ] **Step 1: 关闭 Rerank**

临时改为：

```yaml
rag:
  retrieval:
    rerank-enabled: false
```

- [ ] **Step 2: 重启后端并查询**

Expected:

- 日志出现 `Rerank 引用候选分支: 配置关闭，使用 RRF 排序`。
- 不新增 `RERANK` 检索任务。
- 回答仍然正常。

- [ ] **Step 3: 恢复配置**

恢复为：

```yaml
rag:
  retrieval:
    rerank-enabled: true
```

- [ ] **Step 4: 编译验证**

Run:

```bash
cd /Users/tangjie/javaai/agent/backend
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected: 编译通过。

## 自检清单

- [ ] Rerank 不改变 Milvus schema。
- [ ] Rerank 失败不影响回答生成。
- [ ] Rerank 关闭时不保存 `RERANK` 任务。
- [ ] Rerank 成功时保存 `RERANK` task 和 hit。
- [ ] Java 代码不使用 `var`。
- [ ] Mapper 不新增注解 SQL。
- [ ] 不新增 Java 单元测试。
- [ ] 文档和日志均使用中文。
