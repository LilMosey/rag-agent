# DashScope Embedding 接入 Milvus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有离线索引链路中接入 DashScope `text-embedding-v4`，把每个 chunk 的向量写入 Milvus，作为后续 query/rerank 的基础。

**Architecture:** 第一版只做索引侧 embedding，不做查询、不做 rerank。流程放在 `chunk -> enrichment -> embedding -> Milvus -> READY`，embedding 文本优先使用 enrichment 生成的增强文本，enrichment 失败时降级使用原始 chunk 正文。embedding 或 Milvus 写入失败时，索引任务失败，文件不进入 READY。

**Tech Stack:** Java 21、Spring Boot、DashScope SDK `com.alibaba:dashscope-sdk-java`、Milvus SDK v2 `io.milvus:milvus-sdk-java`、MinIO、MyBatis-Plus。

---

## 文件结构

- 修改：`backend/kb-bootstrap/src/main/resources/application.yml`
  - 增加 `rag.embedding` 配置，包含开关、模型、base-url、api-key、dimension、batch-size。
- 修改：`backend/kb-bootstrap/src/main/resources/application-test.yml`
  - 本地私有配置，写入真实 embedding API key 和 MaaS base-url；该文件已在 `.gitignore` 中，不提交。
- 修改：`backend/pom.xml`
  - 确认 Milvus SDK 版本属性是否已存在，不存在则新增。
- 修改：`backend/kb-infrastructure/pom.xml`
  - 确认 `dashscope-sdk-java` 已存在；新增或确认 `milvus-sdk-java` 依赖。
- 新增：`backend/kb-application/src/main/java/com/example/kb/application/port/EmbeddingGenerator.java`
  - 定义批量生成向量的应用层端口。
- 新增：`backend/kb-application/src/main/java/com/example/kb/application/port/VectorIndexWriter.java`
  - 定义写入 Milvus 的应用层端口。
- 新增：`backend/kb-application/src/main/java/com/example/kb/application/service/ChunkEmbeddingService.java`
  - 组织读取 embedding 文本、调用 embedding、写入向量索引。
- 修改：`backend/kb-application/src/main/java/com/example/kb/application/port/ChunkEnrichmentRepository.java`
  - 增加按 `fileId` 查询 enrichment 列表的方法。
- 修改：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/repository/MybatisChunkEnrichmentRepository.java`
  - 实现按 `fileId` 查询 enrichment 列表。
- 新增：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/embedding/EmbeddingProperties.java`
  - 读取 `rag.embedding` 配置。
- 新增：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/embedding/DashScopeEmbeddingGenerator.java`
  - 使用官方 `TextEmbedding` API 批量生成向量。
- 新增：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/vector/MilvusProperties.java`
  - 读取 `app.vector.milvus` 和 embedding 维度相关配置。
- 新增：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/vector/MilvusVectorIndexWriter.java`
  - 创建 collection、创建索引、写入向量。
- 修改：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/vector/NoopVectorIndexCleaner.java`
  - 替换为真实 Milvus 删除实现，或删除后新增 `MilvusVectorIndexCleaner`。
- 修改：`backend/kb-bootstrap/src/main/java/com/example/kb/bootstrap/ApplicationServiceConfiguration.java`
  - 注册 `ChunkEmbeddingService` 及相关端口实现。
- 修改：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/index/DocumentIndexPipeline.java`
  - 在 enrichment 后调用 embedding。

---

### Task 1: 配置和依赖确认

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/kb-infrastructure/pom.xml`
- Modify: `backend/kb-bootstrap/src/main/resources/application.yml`
- Modify: `backend/kb-bootstrap/src/main/resources/application-test.yml`

- [ ] **Step 1: 确认依赖**

检查 `backend/kb-infrastructure/pom.xml` 中已有：

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>dashscope-sdk-java</artifactId>
    <version>${dashscope.version}</version>
</dependency>
```

如果 `milvus-sdk-java` 还没有加入，则在 `backend/pom.xml` 增加：

```xml
<milvus.version>2.6.17</milvus.version>
```

并在 `backend/kb-infrastructure/pom.xml` 增加：

```xml
<dependency>
    <groupId>io.milvus</groupId>
    <artifactId>milvus-sdk-java</artifactId>
    <version>${milvus.version}</version>
</dependency>
```

- [ ] **Step 2: 增加 embedding 配置**

在 `backend/kb-bootstrap/src/main/resources/application.yml` 中增加：

```yaml
rag:
  embedding:
    enabled: true
    provider: dashscope
    model: text-embedding-v4
    api-key:
    base-url:
    dimension: 1024
    batch-size: 10
```

如果文件中已经有 `rag.enrichment`，保持同级结构，不覆盖原配置。

- [ ] **Step 3: 在本地 test 配置中放私密 key**

在 `backend/kb-bootstrap/src/main/resources/application-test.yml` 中增加：

```yaml
rag:
  embedding:
    api-key: "你的 DashScope API Key"
    base-url: "https://你的WorkspaceId.cn-beijing.maas.aliyuncs.com/api/v1"
```

该文件已被 `.gitignore` 忽略，不能提交。

- [ ] **Step 4: 编译验证依赖**

Run:

```bash
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

---

### Task 2: 定义 embedding 应用层端口

**Files:**
- Create: `backend/kb-application/src/main/java/com/example/kb/application/port/EmbeddingGenerator.java`
- Create: `backend/kb-application/src/main/java/com/example/kb/application/port/VectorIndexWriter.java`
- Create: `backend/kb-application/src/main/java/com/example/kb/application/service/ChunkEmbeddingService.java`

- [ ] **Step 1: 新增 `EmbeddingGenerator`**

创建 `backend/kb-application/src/main/java/com/example/kb/application/port/EmbeddingGenerator.java`：

```java
package com.example.kb.application.port;

import java.util.List;

public interface EmbeddingGenerator {

    GenerateEmbeddingsResult generate(GenerateEmbeddingsCommand command);

    record GenerateEmbeddingsCommand(
            List<String> texts
    ) {
    }

    record GenerateEmbeddingsResult(
            List<EmbeddingItem> items,
            String provider,
            String model,
            Integer totalTokens
    ) {
    }

    record EmbeddingItem(
            int textIndex,
            List<Float> vector
    ) {
    }
}
```

- [ ] **Step 2: 新增 `VectorIndexWriter`**

创建 `backend/kb-application/src/main/java/com/example/kb/application/port/VectorIndexWriter.java`：

```java
package com.example.kb.application.port;

import java.util.List;

public interface VectorIndexWriter {

    void upsertChunks(UpsertChunksCommand command);

    record UpsertChunksCommand(
            Long knowledgeBaseId,
            Long fileId,
            List<VectorChunk> chunks
    ) {
    }

    record VectorChunk(
            Long chunkId,
            int chunkIndex,
            String embeddingSource,
            String contentHash,
            List<Float> vector
    ) {
    }
}
```

- [ ] **Step 3: 新增 `ChunkEmbeddingService` 骨架**

创建 `backend/kb-application/src/main/java/com/example/kb/application/service/ChunkEmbeddingService.java`：

```java
package com.example.kb.application.service;

import com.example.kb.application.port.EmbeddingGenerator;
import com.example.kb.application.port.VectorIndexWriter;
import com.example.kb.domain.model.DocumentChunk;
import com.example.kb.domain.model.KnowledgeFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ChunkEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkEmbeddingService.class);

    private final EmbeddingGenerator embeddingGenerator;
    private final VectorIndexWriter vectorIndexWriter;

    public ChunkEmbeddingService(
            EmbeddingGenerator embeddingGenerator,
            VectorIndexWriter vectorIndexWriter
    ) {
        this.embeddingGenerator = embeddingGenerator;
        this.vectorIndexWriter = vectorIndexWriter;
    }

    public void rebuildEmbeddings(KnowledgeFile file, List<DocumentChunk> chunks) {
        log.info("重建 embedding 入参: knowledgeBaseId={}, fileId={}, chunkCount={}",
                file.knowledgeBaseId(), file.id(), chunks.size());
        log.info("重建 embedding 分支: 端口已接入，具体文本选择和写入在后续任务实现");
    }
}
```

- [ ] **Step 4: 编译验证**

Run:

```bash
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

---

### Task 3: 实现 DashScope embedding

**Files:**
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/embedding/EmbeddingProperties.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/embedding/DashScopeEmbeddingGenerator.java`

- [ ] **Step 1: 新增配置类**

创建 `EmbeddingProperties.java`：

```java
package com.example.kb.infrastructure.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.embedding")
public record EmbeddingProperties(
        boolean enabled,
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        int dimension,
        int batchSize
) {
}
```

- [ ] **Step 2: 新增 DashScope 实现**

创建 `DashScopeEmbeddingGenerator.java`：

```java
package com.example.kb.infrastructure.embedding;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.utils.Constants;
import com.example.kb.application.port.EmbeddingGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DashScopeEmbeddingGenerator implements EmbeddingGenerator {

    private static final Logger log = LoggerFactory.getLogger(DashScopeEmbeddingGenerator.class);

    private final EmbeddingProperties properties;
    private final TextEmbedding textEmbedding;

    public DashScopeEmbeddingGenerator(EmbeddingProperties properties) {
        this.properties = properties;
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            Constants.apiKey = properties.apiKey();
        }
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            Constants.baseHttpApiUrl = properties.baseUrl();
        }
        this.textEmbedding = new TextEmbedding();
    }

    @Override
    public GenerateEmbeddingsResult generate(GenerateEmbeddingsCommand command) {
        log.info("DashScope embedding 入参: textCount={}, model={}", command.texts().size(), properties.model());
        try {
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(properties.model())
                    .texts(command.texts())
                    .build();
            TextEmbeddingResult result = textEmbedding.call(param);
            List<TextEmbeddingResultItem> resultItems = result.getOutput().getEmbeddings().stream()
                    .sorted(Comparator.comparing(TextEmbeddingResultItem::getTextIndex))
                    .toList();
            List<EmbeddingItem> items = new ArrayList<>(resultItems.size());
            for (TextEmbeddingResultItem resultItem : resultItems) {
                List<Float> vector = toFloatVector(resultItem.getEmbedding());
                validateDimension(vector);
                items.add(new EmbeddingItem(resultItem.getTextIndex(), vector));
            }
            Integer totalTokens = result.getUsage() == null ? null : result.getUsage().getTotalTokens();
            log.info("DashScope embedding 出参: textCount={}, vectorCount={}, totalTokens={}",
                    command.texts().size(), items.size(), totalTokens);
            return new GenerateEmbeddingsResult(items, properties.provider(), properties.model(), totalTokens);
        } catch (Exception exception) {
            log.error("DashScope embedding 异常: textCount={}, model={}", command.texts().size(), properties.model(), exception);
            throw new IllegalStateException("DashScope embedding 生成失败: " + exception.getMessage(), exception);
        }
    }

    private List<Float> toFloatVector(List<Double> embedding) {
        List<Float> vector = new ArrayList<>(embedding.size());
        for (Double value : embedding) {
            vector.add(value.floatValue());
        }
        return vector;
    }

    private void validateDimension(List<Float> vector) {
        if (properties.dimension() > 0 && vector.size() != properties.dimension()) {
            throw new IllegalStateException("embedding 维度不匹配，期望=" + properties.dimension() + "，实际=" + vector.size());
        }
    }
}
```

- [ ] **Step 3: 编译验证**

Run:

```bash
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

---

### Task 4: 实现 enrichment 查询和 embedding 文本选择

**Files:**
- Modify: `backend/kb-application/src/main/java/com/example/kb/application/port/ChunkEnrichmentRepository.java`
- Modify: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/repository/MybatisChunkEnrichmentRepository.java`
- Modify: `backend/kb-application/src/main/java/com/example/kb/application/service/ChunkEmbeddingService.java`
- Reuse: `backend/kb-application/src/main/java/com/example/kb/application/port/ChunkContentStorage.java`

- [ ] **Step 1: 为 enrichment repository 增加查询方法**

在 `ChunkEnrichmentRepository` 增加：

```java
List<ChunkEnrichment> findByFileId(Long fileId);
```

- [ ] **Step 2: 实现 enrichment 查询**

在 `MybatisChunkEnrichmentRepository` 增加：

```java
@Override
public List<ChunkEnrichment> findByFileId(Long fileId) {
    log.info("查询 enrichment 元数据入参: fileId={}", fileId);
    LambdaQueryWrapper<ChunkEnrichmentEntity> wrapper = new LambdaQueryWrapper<ChunkEnrichmentEntity>()
            .eq(ChunkEnrichmentEntity::getFileId, fileId);
    List<ChunkEnrichment> enrichments = chunkEnrichmentMapper.selectList(wrapper).stream()
            .map(this::toDomain)
            .toList();
    log.info("查询 enrichment 元数据出参: fileId={}, count={}", fileId, enrichments.size());
    return enrichments;
}
```

- [ ] **Step 3: 完整实现 `ChunkEmbeddingService`**

把 `ChunkEmbeddingService` 构造参数扩展为：

```java
private final ChunkContentStorage chunkContentStorage;
private final ChunkEnrichmentRepository chunkEnrichmentRepository;
private final ChunkEnrichmentObjectStorage chunkEnrichmentObjectStorage;
private final EmbeddingGenerator embeddingGenerator;
private final VectorIndexWriter vectorIndexWriter;
```

文本选择规则：

```text
1. 如果 enrichment status = READY 且 embeddingTextObjectKey 非空，读取 enrichment embedding_text。
2. 否则读取原始 chunk 正文。
3. embedding_source 分别记录 ENRICHMENT_TEXT 或 ORIGINAL_CHUNK。
```

分批规则：

```text
使用 rag.embedding.batch-size，默认建议 10。
每批调用一次 EmbeddingGenerator.generate。
每批得到向量后组装 VectorIndexWriter.VectorChunk。
所有批次完成后调用 VectorIndexWriter.upsertChunks。
```

失败规则：

```text
任何 embedding 或 Milvus 写入异常直接抛出。
DocumentIndexPipeline 捕获后会把文件置为 PARSE_FAILED，任务置为 FAILED。
```

- [ ] **Step 4: 编译验证**

Run:

```bash
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

---

### Task 5: 实现 Milvus 写入和删除

**Files:**
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/vector/MilvusProperties.java`
- Create: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/vector/MilvusVectorIndexWriter.java`
- Modify: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/vector/NoopVectorIndexCleaner.java`

- [ ] **Step 1: 新增 Milvus 配置类**

创建 `MilvusProperties.java`：

```java
package com.example.kb.infrastructure.vector;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vector.milvus")
public record MilvusProperties(
        String host,
        int port,
        String database,
        String collectionPrefix
) {

    public String uri() {
        return "http://" + host + ":" + port;
    }

    public String collectionName() {
        return collectionPrefix + "_chunk";
    }
}
```

- [ ] **Step 2: 新增 Milvus writer**

创建 `MilvusVectorIndexWriter.java`，核心行为：

```text
1. 构造 MilvusClientV2。
2. 若 collection 不存在，创建 collection。
3. collection 字段：
   - id: Int64 primary key, autoID=true
   - knowledge_base_id: Int64
   - file_id: Int64
   - chunk_id: Int64
   - chunk_index: Int32
   - embedding_source: VarChar(64)
   - content_hash: VarChar(128)
   - vector: FloatVector(dimension)
4. 写入前先按 file_id 删除旧向量，保证重建幂等。
5. 每次 insert 最多 100 条。
```

Milvus SDK 使用方向：

```java
ConnectConfig connectConfig = ConnectConfig.builder()
        .uri(milvusProperties.uri())
        .dbName(milvusProperties.database())
        .build();
MilvusClientV2 client = new MilvusClientV2(connectConfig);
```

insert 使用：

```java
InsertReq insertReq = InsertReq.builder()
        .databaseName(milvusProperties.database())
        .collectionName(milvusProperties.collectionName())
        .data(rows)
        .build();
client.insert(insertReq);
```

delete 使用：

```java
DeleteReq deleteReq = DeleteReq.builder()
        .databaseName(milvusProperties.database())
        .collectionName(milvusProperties.collectionName())
        .filter("file_id == " + fileId)
        .build();
client.delete(deleteReq);
```

- [ ] **Step 3: 替换 `NoopVectorIndexCleaner`**

把 `NoopVectorIndexCleaner.deleteByFileId` 改成真实调用 Milvus：

```text
1. 如果 collection 不存在，直接返回成功。
2. 如果存在，按 file_id 删除。
3. 删除失败抛出异常。
```

- [ ] **Step 4: 编译验证**

Run:

```bash
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

---

### Task 6: 接入索引 Pipeline 和 Spring 配置

**Files:**
- Modify: `backend/kb-bootstrap/src/main/java/com/example/kb/bootstrap/ApplicationServiceConfiguration.java`
- Modify: `backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/index/DocumentIndexPipeline.java`

- [ ] **Step 1: 注册配置属性**

在启动类或配置类中确保包含：

```java
@EnableConfigurationProperties({
        ChunkEnrichmentProperties.class,
        EmbeddingProperties.class,
        MilvusProperties.class
})
```

如果当前项目已经通过其他方式启用 `ChunkEnrichmentProperties`，保持现有方式，只补 `EmbeddingProperties` 和 `MilvusProperties`。

- [ ] **Step 2: 注册 `ChunkEmbeddingService`**

在 `ApplicationServiceConfiguration` 中增加：

```java
@Bean
public ChunkEmbeddingService chunkEmbeddingService(
        ChunkContentStorage chunkContentStorage,
        ChunkEnrichmentRepository chunkEnrichmentRepository,
        ChunkEnrichmentObjectStorage chunkEnrichmentObjectStorage,
        EmbeddingGenerator embeddingGenerator,
        VectorIndexWriter vectorIndexWriter
) {
    return new ChunkEmbeddingService(
            chunkContentStorage,
            chunkEnrichmentRepository,
            chunkEnrichmentObjectStorage,
            embeddingGenerator,
            vectorIndexWriter
    );
}
```

- [ ] **Step 3: 在 `DocumentIndexPipeline` 接入 embedding**

构造函数增加：

```java
private final ChunkEmbeddingService chunkEmbeddingService;
```

在 enrichment 后增加：

```java
chunkEmbeddingService.rebuildEmbeddings(file, chunks);
```

message 改为：

```java
String message = "文档解析、清洗、chunk、enrichment 和 embedding 处理完成，sectionCount=" + sectionCount + ", chunkCount=" + chunkCount;
```

- [ ] **Step 4: 编译验证**

Run:

```bash
/Users/tangjie/software/apache-maven-3.6.3/bin/mvn -q -Dmaven.repo.local=/Users/tangjie/mvnRepo -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

---

### Task 7: 手工验证流程

**Files:**
- No code changes.

- [ ] **Step 1: 启动外部服务**

用户在项目根目录执行：

```bash
docker compose up -d
```

Expected:

```text
agent-mysql、agent-minio、agent-milvus、agent-etcd、agent-attu 处于 Up 状态
```

- [ ] **Step 2: 启动后端**

用户启动 Spring Boot 后端，确保激活 `test` profile，且 `application-test.yml` 内有 DashScope embedding key。

- [ ] **Step 3: 上传一个小文档**

在前端上传一个 txt/markdown/word 文档。

Expected backend logs contain:

```text
重建 chunk 出参
重建 enrichment 出参
DashScope embedding 出参
Milvus 向量写入出参
索引 Pipeline 出参
```

- [ ] **Step 4: 在 Attu 检查 Milvus**

打开：

```text
http://localhost:8000
```

检查 collection：

```text
enterprise_kb_chunk
```

期望看到字段：

```text
knowledge_base_id
file_id
chunk_id
chunk_index
embedding_source
content_hash
vector
```

- [ ] **Step 5: 删除文件验证向量删除**

在前端删除该文件。

Expected backend logs contain:

```text
删除向量索引入参
删除向量索引出参
```

Attu 中该 `file_id` 的向量数据应被删除。

---

## 自检

- 已覆盖 DashScope SDK 官方 `TextEmbedding` 调用方式。
- 已覆盖 MaaS 北京地域 `Constants.baseHttpApiUrl` 可配置。
- 已覆盖 API key 不提交 git 的要求。
- 已明确 embedding 失败时索引任务失败。
- 已明确第一版只做索引侧 embedding，不做 query/rerank。
- 已遵守项目约束：不写 Java 单元测试，不使用 `var`，计划文档使用中文。
