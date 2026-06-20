# 索引构建 Pipeline 重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将索引构建入口 `DocumentIndexPipeline#execute` 中硬编码的解析、清洗、chunk、enrichment、embedding 串行逻辑拆成可扩展的 Pipeline Step。

**Architecture:** 使用 `IndexBuildContext` 保存索引任务执行过程中的共享状态，使用 `IndexBuildStep` 表示每个构建阶段。`DocumentIndexPipeline` 只负责加载文件、设置状态、按顺序执行步骤、统一处理成功和异常。

**Tech Stack:** Java 21、Spring Boot `@Component` + `@Order`、现有 `DocumentParser`、`DocumentCleaner`、`DocumentChunkService`、`ChunkEnrichmentService`、`ChunkEmbeddingService`。

---

### Task 1: 新增索引构建上下文与步骤接口

**Files:**
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/index/IndexBuildContext.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/index/IndexBuildStep.java`

- [x] **Step 1: 新增 `IndexBuildContext`**

保存 `KnowledgeFileIndexTask`、`KnowledgeFile`、`ParsedDocument`、`cleanedDocument`、`chunks` 等索引过程数据。

- [x] **Step 2: 新增 `IndexBuildStep`**

接口方法：

```java
String name();

void execute(IndexBuildContext context);
```

---

### Task 2: 拆分五个索引构建步骤

**Files:**
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/index/ParseDocumentIndexBuildStep.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/index/CleanDocumentIndexBuildStep.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/index/ChunkDocumentIndexBuildStep.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/index/EnrichChunkIndexBuildStep.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/index/EmbedChunkIndexBuildStep.java`

- [x] **Step 1: 解析步骤**

从 MinIO 读取原文件，调用 `DocumentParserRegistry` 找 parser，并生成 `ParsedDocument`。

- [x] **Step 2: 清洗步骤**

调用 `DocumentCleaner#clean`，保存清洗后的 `ParsedDocument`。

- [x] **Step 3: Chunk 步骤**

调用 `DocumentChunkService#rebuildChunks`，保存 `List<DocumentChunk>`。

- [x] **Step 4: Enrichment 步骤**

调用 `ChunkEnrichmentService#rebuildEnrichments`。

- [x] **Step 5: Embedding 步骤**

调用 `ChunkEmbeddingService#rebuildEmbeddings`。

---

### Task 3: 改造 DocumentIndexPipeline

**Files:**
- Modify: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/index/DocumentIndexPipeline.java`

- [x] **Step 1: 构造器改为接收 `List<IndexBuildStep>`**

保留 `KnowledgeFileRepository`，移除 parser、cleaner、chunk、enrichment、embedding 的直接依赖。

- [x] **Step 2: execute 改为执行步骤列表**

流程：
- 查询文件元数据
- 更新文件状态为 `PARSING`
- 创建 `IndexBuildContext`
- 遍历 `indexBuildSteps`
- 每一步打入参/出参日志
- 成功后更新文件状态为 `READY`
- 异常时更新为 `PARSE_FAILED`

- [x] **Step 3: 成功消息保留 section/chunk 数量**

成功消息继续包含 `sectionCount` 和 `chunkCount`，方便和旧行为对齐。

---

### Task 4: 编译验证

**Files:**
- No code file changes expected in this task.

- [x] **Step 1: 后端编译**

Run:

```bash
cd backend && /Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected: 命令退出码为 `0`。

- [x] **Step 2: 检查 Java 代码没有 `var`**

Run:

```bash
rg "\bvar\b" backend --glob "*.java"
```

Expected: 无输出。
