# RAG 查询增强 V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 V1 基础 RAG 查询闭环上增加 Query 改写、HyDE、多 Query、并发多路检索、RRF 融合、检索任务记录和流式输出，并为 BM25/混合检索预留清晰迁移边界。

**Architecture:** V2 先拆成两个可验证阶段。阶段 A 不变更 Milvus collection schema，先实现 Query 改写、HyDE、多 Query、线程池并发 Dense 检索、RRF、检索任务记录和 SSE 流式回答；阶段 B 新建 `enterprise_kb_chunk_v2` 支持 BM25/full text search，再接入 Dense + BM25 混合检索。

**Tech Stack:** Java、Spring Boot、Spring SSE、MyBatis-Plus、MyBatis XML、MySQL、Milvus Java SDK、DashScope Embedding、AgentScope Java、React、TypeScript、Ant Design。

---

## 阶段 A 范围

- Query 改写。
- HyDE。
- 多 Query。
- 多路 Dense 检索线程池并发。
- 单路失败降级。
- 按 `chunk_id` 去重。
- RRF 融合排序。
- 检索任务记录和命中记录。
- SSE 流式回答接口。
- 前端流式展示回答。

## 阶段 A 不做

- BM25。
- Milvus V2 collection。
- Rerank。
- 上下文压缩。
- 记忆系统。
- 前端展示 Query 改写、HyDE、多 Query 内部信息。

## 阶段 B 范围

- 新建 `enterprise_kb_chunk_v2` collection。
- 写入 text 字段和 BM25 sparse vector。
- BM25 检索端口。
- Dense + BM25 混合检索。
- BM25 检索任务记录。

## Task 1: 检索增强领域模型和端口

**Files:**
- Create: `backend/kb-domain/src/main/java/com/example/kb/domain/model/RetrievalTaskType.java`
- Create: `backend/kb-domain/src/main/java/com/example/kb/domain/model/RetrievalTaskStatus.java`
- Create: `backend/kb-domain/src/main/java/com/example/kb/domain/model/ConversationRetrievalTask.java`
- Create: `backend/kb-domain/src/main/java/com/example/kb/domain/model/ConversationRetrievalTaskHit.java`
- Create: `backend/kb-application/src/main/java/com/example/kb/application/port/QueryRewriteGenerator.java`
- Create: `backend/kb-application/src/main/java/com/example/kb/application/port/HydeGenerator.java`
- Create: `backend/kb-application/src/main/java/com/example/kb/application/port/MultiQueryGenerator.java`
- Create: `backend/kb-application/src/main/java/com/example/kb/application/port/ConversationRetrievalTaskRepository.java`
- Create: `backend/kb-application/src/main/java/com/example/kb/application/port/ConversationRetrievalTaskHitRepository.java`

- [ ] 新增检索任务类型：`ORIGINAL_DENSE`、`REWRITE_DENSE`、`HYDE_DENSE`、`MULTI_QUERY_DENSE`、`BM25`。
- [ ] 新增检索任务状态：`PENDING`、`RUNNING`、`SUCCESS`、`FAILED`、`SKIPPED`。
- [ ] 新增 Query 改写、HyDE、多 Query 端口，返回文本、provider、model、失败原因。
- [ ] 新增检索任务和命中记录 Repository 端口。
- [ ] 后端编译通过。

## Task 2: 检索任务 MySQL 持久化

**Files:**
- Modify: `backend/kb-infrastructure/src/main/resources/db/schema.sql`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/entity/ConversationRetrievalTaskEntity.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/entity/ConversationRetrievalTaskHitEntity.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/mapper/ConversationRetrievalTaskMapper.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/mapper/ConversationRetrievalTaskHitMapper.java`
- Create: `backend/kb-infrastructure/src/main/resources/mapper/ConversationRetrievalTaskMapper.xml`
- Create: `backend/kb-infrastructure/src/main/resources/mapper/ConversationRetrievalTaskHitMapper.xml`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/repository/MybatisConversationRetrievalTaskRepository.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/repository/MybatisConversationRetrievalTaskHitRepository.java`

- [ ] 新增 `conversation_retrieval_task` 表。
- [ ] 新增 `conversation_retrieval_task_hit` 表。
- [ ] 批量保存 hit 使用 XML，不在 Mapper 写 SQL 注解。
- [ ] 后端编译通过。

## Task 3: Query 改写、HyDE、多 Query AgentScope 实现

**Files:**
- Modify: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/rag/RagPromptBuilder.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/rag/AgentScopeQueryRewriteGenerator.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/rag/AgentScopeHydeGenerator.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/rag/AgentScopeMultiQueryGenerator.java`
- Modify: `backend/kb-bootstrap/src/main/resources/application.yml`
- Modify: `backend/kb-bootstrap/src/main/java/com/example/kb/bootstrap/ApplicationServiceConfiguration.java`

- [ ] 新增 `rag.retrieval` 配置。
- [ ] Query 改写只使用当前问题和最近 6 条消息。
- [ ] HyDE 文本只用于检索，不进入前端展示。
- [ ] 多 Query 默认生成 3 条。
- [ ] 三个生成器失败时抛出异常，由编排层降级跳过。
- [ ] 后端编译通过。

## Task 4: 并发 Dense 检索和 RRF 融合

**Files:**
- Create: `backend/kb-application/src/main/java/com/example/kb/application/service/RagRetrievalService.java`
- Create: `backend/kb-application/src/main/java/com/example/kb/application/service/RrfFusionService.java`
- Modify: `backend/kb-application/src/main/java/com/example/kb/application/service/RagChatService.java`
- Modify: `backend/kb-bootstrap/src/main/java/com/example/kb/bootstrap/ApplicationServiceConfiguration.java`

- [ ] 构建检索任务：原始 query、改写 query、HyDE、多 Query。
- [ ] 使用 `ThreadPoolTaskExecutor` 并发执行检索任务。
- [ ] 每个任务失败只标记该任务失败，不影响其他任务。
- [ ] 保存任务记录和任务命中记录。
- [ ] 使用 RRF 按 `chunk_id` 融合排序。
- [ ] RagChatService 使用融合结果回查 MySQL/MinIO。
- [ ] 后端编译通过。

## Task 5: SSE 流式回答

**Files:**
- Modify: `backend/kb-application/src/main/java/com/example/kb/application/port/RagAnswerGenerator.java`
- Modify: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/rag/AgentScopeRagAnswerGenerator.java`
- Modify: `backend/kb-application/src/main/java/com/example/kb/application/service/RagChatService.java`
- Modify: `backend/kb-api/src/main/java/com/example/kb/api/controller/ConversationController.java`
- Modify: `backend/kb-api/src/main/java/com/example/kb/api/dto/ConversationDtos.java`

- [ ] RagAnswerGenerator 增加流式方法。
- [ ] AgentScopeRagAnswerGenerator 支持 delta 回调。
- [ ] 新增 `POST /api/conversations/{conversationId}/messages/stream`。
- [ ] SSE 事件包括：`router`、`retrieval_done`、`answer_delta`、`answer_done`、`references`、`error`。
- [ ] 流式结束后保存完整 assistant 消息和引用。
- [ ] 后端编译通过。

## Task 6: 前端流式展示

**Files:**
- Modify: `frontend/src/api/conversationApi.ts`
- Modify: `frontend/src/pages/ConversationPage.tsx`
- Modify: `frontend/src/components/ChatMessageList.tsx`
- Modify: `frontend/src/types/domain.ts`

- [ ] 发送消息改为调用流式接口。
- [ ] `answer_delta` 到达时追加到当前 assistant 消息。
- [ ] `references` 到达后展示引用来源。
- [ ] 异常时展示错误提示。
- [ ] TypeScript 检查通过。

## Task 7: 阶段 B BM25 设计落地前检查

**Files:**
- No code files.

- [ ] 检查 Milvus 2.6 Java SDK 创建 BM25 function 的实际 API。
- [ ] 用临时 collection 验证 BM25 full text search。
- [ ] 确认 `enterprise_kb_chunk_v2` schema。
- [ ] 确认旧 collection 数据迁移策略。

## 验证

- 后端：`/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile`
- Java 约束：`rg "\\bvar\\b" backend --glob "*.java"` 无输出。
- Mapper SQL：`rg "@Insert|@Update|@Delete|@Select" backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/mapper` 无新增输出。
- 前端类型：`cd frontend && ./node_modules/.bin/tsc --noEmit`

