# 企业知识库文件管理实现计划

**目标：** 构建企业知识库文件管理第一版，包含知识库管理、单文件上传、文件列表、详情、下载、删除和前端管理台。

**架构：** 后端采用 Spring Boot Maven 多模块，按 `api/application/domain/infrastructure/bootstrap` 分层。MySQL 保存业务元数据，MinIO 保存原始文件，Milvus 只保留删除端口和空实现。前端采用 React + Vite + TypeScript + Ant Design。

**技术栈：** Java 21、Spring Boot、Maven、MyBatis-Plus、MySQL、MinIO Java SDK、React、Vite、TypeScript、Ant Design。

---

## 实现约束

- `createdAt`、`updatedAt` 在 Java 代码中统一使用 `java.time.LocalDateTime`。
- Java 代码中不使用 `var`，所有局部变量必须写明具体类型。
- 本期不生成 Java 单元测试代码，用户会自行审阅代码。
- MySQL、MinIO、Milvus 由用户安装或启动，Codex 不自动安装外部软件。
- Milvus 第一版不参与运行，只实现 `VectorIndexCleaner` 空实现。
- 当前目录不是 git 仓库；如未初始化 git，则跳过提交步骤。

## 文件结构

```text
backend/
  pom.xml
  kb-domain/
  kb-application/
  kb-api/
  kb-infrastructure/
  kb-bootstrap/
frontend/
README.md
```

---

## 任务 1：创建 README

**文件：**
- 新建：`README.md`

**步骤：**

1. 写明项目第一版范围：只做知识库和文件管理，不做登录、RAG、向量写入、检索或 Agent 应用。
2. 写明外部软件由用户安装：MySQL、MinIO、Milvus。
3. 写明前端启动命令：

```text
cd frontend
npm install
npm run start
```

4. 写明后端预期启动命令：

```text
cd backend
mvn spring-boot:run -pl kb-bootstrap
```

**验证：**

```bash
sed -n '1,220p' README.md
```

预期：能看到外部软件由用户安装、前端启动命令、后端启动命令。

---

## 任务 2：创建后端 Maven 多模块骨架

**文件：**
- 新建：`backend/pom.xml`
- 新建：`backend/kb-domain/pom.xml`
- 新建：`backend/kb-application/pom.xml`
- 新建：`backend/kb-api/pom.xml`
- 新建：`backend/kb-infrastructure/pom.xml`
- 新建：`backend/kb-bootstrap/pom.xml`

**要求：**

- 父工程 artifactId：`enterprise-kb`
- groupId：`com.example`
- Java 版本建议：21
- Spring Boot 版本在实现前按 AgentScope Java Spring Boot Starter 兼容性确认。
- `kb-domain` 不依赖 Spring、MyBatis-Plus、MinIO、Milvus。
- `kb-application` 依赖 `kb-domain`。
- `kb-api` 依赖 `kb-application` 和 Spring Web。
- `kb-infrastructure` 依赖 `kb-application`、MyBatis-Plus、MySQL Driver、MinIO SDK。
- `kb-bootstrap` 依赖 `kb-api` 和 `kb-infrastructure`。
- 不添加 JUnit、Mockito 等 Java 测试依赖。

**验证：**

```bash
cd backend
mvn -q -DskipTests validate
```

预期：Maven 能识别所有模块。若网络受限导致依赖下载失败，需要用户允许网络访问或在本机手动处理。

---

## 任务 3：实现领域模型和文件类型策略

**文件：**
- 新建：`backend/kb-domain/src/main/java/com/example/kb/domain/model/FileStatus.java`
- 新建：`backend/kb-domain/src/main/java/com/example/kb/domain/model/FileType.java`
- 新建：`backend/kb-domain/src/main/java/com/example/kb/domain/model/KnowledgeBase.java`
- 新建：`backend/kb-domain/src/main/java/com/example/kb/domain/model/KnowledgeFile.java`
- 新建：`backend/kb-domain/src/main/java/com/example/kb/domain/service/FileTypePolicy.java`

**核心代码要求：**

`FileStatus`：

```java
package com.example.kb.domain.model;

public enum FileStatus {
    UPLOADED,
    PENDING_PARSE,
    PARSING,
    PARSE_FAILED,
    READY,
    DISABLED
}
```

`FileType`：

```java
package com.example.kb.domain.model;

public enum FileType {
    WORD,
    MARKDOWN,
    TEXT,
    PDF_RESERVED
}
```

`KnowledgeBase` 使用 `LocalDateTime`：

```java
package com.example.kb.domain.model;

import java.time.LocalDateTime;

public record KnowledgeBase(
        Long id,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

`KnowledgeFile` 使用 `LocalDateTime`：

```java
package com.example.kb.domain.model;

import java.time.LocalDateTime;

public record KnowledgeFile(
        Long id,
        Long knowledgeBaseId,
        String originalFilename,
        String fileExt,
        String contentType,
        long fileSize,
        String checksumSha256,
        String storageBucket,
        String storageObjectKey,
        FileType fileType,
        FileStatus fileStatus,
        String parseError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

`FileTypePolicy`：

```java
package com.example.kb.domain.service;

import com.example.kb.domain.model.FileType;

import java.util.Locale;

public class FileTypePolicy {

    public FileType detect(String filename) {
        String ext = extensionOf(filename);
        return switch (ext) {
            case "doc", "docx" -> FileType.WORD;
            case "md", "markdown" -> FileType.MARKDOWN;
            case "txt" -> FileType.TEXT;
            case "pdf" -> throw new IllegalArgumentException("当前版本不支持上传 PDF 文件。");
            default -> throw new IllegalArgumentException("仅支持上传 Word、Markdown、TXT 文件。");
        };
    }

    public String extensionOf(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空。");
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("文件缺少扩展名。");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
```

**验证：**

```bash
cd backend
mvn -q -pl kb-domain -am -DskipTests compile
```

预期：编译通过。

---

## 任务 4：实现应用层端口和服务

**文件：**
- 新建：`backend/kb-application/src/main/java/com/example/kb/application/port/KnowledgeBaseRepository.java`
- 新建：`backend/kb-application/src/main/java/com/example/kb/application/port/KnowledgeFileRepository.java`
- 新建：`backend/kb-application/src/main/java/com/example/kb/application/port/ObjectStorage.java`
- 新建：`backend/kb-application/src/main/java/com/example/kb/application/port/VectorIndexCleaner.java`
- 新建：`backend/kb-application/src/main/java/com/example/kb/application/service/KnowledgeBaseService.java`
- 新建：`backend/kb-application/src/main/java/com/example/kb/application/service/KnowledgeFileService.java`

**要求：**

- `KnowledgeBaseService` 支持创建、列表、详情、更新、删除空知识库。
- 删除知识库时，如果存在文件，抛出错误：“知识库下仍有文件，请先删除文件。”
- `KnowledgeFileService` 支持上传、列表、详情、下载、删除。
- 上传前检查同一知识库下是否存在同名文件。
- 重名错误：“同一知识库下已存在同名文件，请先删除旧文件。”
- 上传成功后状态为 `UPLOADED`。
- 删除顺序：先 `VectorIndexCleaner`，再 `ObjectStorage`，最后删除 MySQL 元数据。
- Java 代码中不使用 `var`。

**关键删除逻辑：**

```java
public void delete(Long knowledgeBaseId, Long fileId) {
    KnowledgeFile file = fileRepository.findByKnowledgeBaseIdAndFileId(knowledgeBaseId, fileId)
            .orElseThrow(() -> new IllegalArgumentException("文件不存在。"));
    vectorIndexCleaner.deleteByFileId(fileId);
    objectStorage.deleteObject(file.storageBucket(), file.storageObjectKey());
    fileRepository.deleteByKnowledgeBaseIdAndFileId(knowledgeBaseId, fileId);
}
```

**关键上传逻辑：**

```java
LocalDateTime now = LocalDateTime.now();
FileType fileType = fileTypePolicy.detect(filename);
ObjectStorage.StoredObject storedObject = objectStorage.putObject(command);
KnowledgeFile file = new KnowledgeFile(
        null,
        knowledgeBaseId,
        filename,
        fileTypePolicy.extensionOf(filename),
        contentType,
        size,
        storedObject.checksumSha256(),
        storedObject.bucket(),
        storedObject.objectKey(),
        fileType,
        FileStatus.UPLOADED,
        null,
        now,
        now
);
```

**验证：**

```bash
cd backend
mvn -q -pl kb-application -am -DskipTests compile
```

预期：编译通过。

---

## 任务 5：实现后端启动类和配置

**文件：**
- 新建：`backend/kb-bootstrap/src/main/java/com/example/kb/bootstrap/KbApplication.java`
- 新建：`backend/kb-bootstrap/src/main/resources/application.yml`
- 新建：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/vector/NoopVectorIndexCleaner.java`

**要求：**

- 启动类扫描 `com.example.kb`。
- MyBatis Mapper 扫描 `com.example.kb.infrastructure.persistence.mapper`。
- 配置文件包含 MySQL、MinIO、文件大小限制。
- MinIO、MySQL 密码使用占位值，等待用户安装外部软件后替换。

`NoopVectorIndexCleaner`：

```java
package com.example.kb.infrastructure.vector;

import com.example.kb.application.port.VectorIndexCleaner;
import org.springframework.stereotype.Component;

@Component
public class NoopVectorIndexCleaner implements VectorIndexCleaner {

    @Override
    public void deleteByFileId(Long fileId) {
        // 第一版不写入 Milvus，删除向量数据时保持空实现。
    }
}
```

**验证：**

```bash
cd backend
mvn -q -pl kb-bootstrap -am -DskipTests compile
```

预期：编译通过；如果持久化 Bean 尚未完成，则继续执行后续任务。

---

## 任务 6：实现 MySQL 表结构与 MyBatis-Plus 持久化

**文件：**
- 新建：`backend/kb-infrastructure/src/main/resources/db/schema.sql`
- 新建：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/entity/KnowledgeBaseEntity.java`
- 新建：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/entity/KnowledgeFileEntity.java`
- 新建：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/mapper/KnowledgeBaseMapper.java`
- 新建：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/mapper/KnowledgeFileMapper.java`
- 新建：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/repository/MybatisKnowledgeBaseRepository.java`
- 新建：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/persistence/repository/MybatisKnowledgeFileRepository.java`

**要求：**

- 数据库字段 `created_at`、`updated_at` 使用 `DATETIME(6)`。
- Java Entity 字段 `createdAt`、`updatedAt` 使用 `LocalDateTime`。
- `knowledge_file` 创建唯一索引：`knowledge_base_id + original_filename`。
- Repository 负责 Entity 和领域对象转换。
- Java 代码中不使用 `var`。

**schema：**

```sql
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1024) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_ext VARCHAR(32) NOT NULL,
    content_type VARCHAR(255) NULL,
    file_size BIGINT NOT NULL,
    checksum_sha256 VARCHAR(128) NOT NULL,
    storage_bucket VARCHAR(255) NOT NULL,
    storage_object_key VARCHAR(1024) NOT NULL,
    file_type VARCHAR(64) NOT NULL,
    file_status VARCHAR(64) NOT NULL,
    parse_error VARCHAR(2048) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_kb_filename (knowledge_base_id, original_filename),
    KEY idx_knowledge_base_id (knowledge_base_id),
    KEY idx_file_status (file_status)
);
```

**验证：**

```bash
cd backend
mvn -q -pl kb-infrastructure -am -DskipTests compile
```

预期：编译通过。此步骤不要求连接 MySQL。

---

## 任务 7：实现 MinIO 存储适配器

**文件：**
- 新建：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/storage/MinioProperties.java`
- 新建：`backend/kb-infrastructure/src/main/java/com/example/kb/infrastructure/storage/MinioObjectStorage.java`

**要求：**

- `MinioProperties` 使用 `@ConfigurationProperties(prefix = "app.storage.minio")`。
- object key 使用稳定结构，例如：

```text
knowledge-bases/{knowledgeBaseId}/files/{uuid}/{originalFilename}
```

- 上传时计算 SHA256。
- 下载时根据 bucket 和 object key 读取。
- 删除时根据 bucket 和 object key 删除。
- Java 代码中不使用 `var`。

**验证：**

```bash
cd backend
mvn -q -pl kb-infrastructure -am -DskipTests compile
```

预期：编译通过。真实上传下载验证需要用户安装并启动 MinIO。

---

## 任务 8：实现 REST API 与全局错误处理

**文件：**
- 新建：`backend/kb-api/src/main/java/com/example/kb/api/dto/ApiResponse.java`
- 新建：`backend/kb-api/src/main/java/com/example/kb/api/dto/KnowledgeBaseDtos.java`
- 新建：`backend/kb-api/src/main/java/com/example/kb/api/dto/KnowledgeFileDtos.java`
- 新建：`backend/kb-api/src/main/java/com/example/kb/api/controller/KnowledgeBaseController.java`
- 新建：`backend/kb-api/src/main/java/com/example/kb/api/controller/KnowledgeFileController.java`
- 新建：`backend/kb-api/src/main/java/com/example/kb/api/exception/GlobalExceptionHandler.java`

**接口：**

```text
POST   /api/knowledge-bases
GET    /api/knowledge-bases
GET    /api/knowledge-bases/{id}
PUT    /api/knowledge-bases/{id}
DELETE /api/knowledge-bases/{id}

POST   /api/knowledge-bases/{kbId}/files
GET    /api/knowledge-bases/{kbId}/files
GET    /api/knowledge-bases/{kbId}/files/{fileId}
GET    /api/knowledge-bases/{kbId}/files/{fileId}/download
DELETE /api/knowledge-bases/{kbId}/files/{fileId}
```

**要求：**

- 上传接口使用 `MultipartFile`。
- `IllegalArgumentException` 返回 HTTP 400。
- 错误响应使用统一结构。
- 下载接口返回文件流。
- Java 代码中不使用 `var`。

**验证：**

```bash
cd backend
mvn -q -pl kb-api -am -DskipTests compile
```

预期：编译通过。

---

## 任务 9：创建前端 Vite React TypeScript 工程

**文件：**
- 新建：`frontend/package.json`
- 新建：`frontend/index.html`
- 新建：`frontend/tsconfig.json`
- 新建：`frontend/vite.config.ts`
- 新建：`frontend/src/main.tsx`
- 新建：`frontend/src/App.tsx`
- 新建：`frontend/src/styles.css`

**要求：**

- 使用 React + Vite + TypeScript + Ant Design。
- `package.json` 提供：

```json
{
  "scripts": {
    "start": "vite --host 0.0.0.0",
    "build": "tsc && vite build",
    "preview": "vite preview"
  }
}
```

- `App.tsx` 加载中文 Ant Design locale。
- 不做登录页。

**验证：**

```bash
cd frontend
npm install
npm run build
```

预期：构建通过。若网络受限导致依赖安装失败，需要用户允许网络访问或手动执行。

---

## 任务 10：实现前端管理台

**文件：**
- 新建：`frontend/src/types/domain.ts`
- 新建：`frontend/src/api/client.ts`
- 新建：`frontend/src/api/knowledgeBaseApi.ts`
- 新建：`frontend/src/api/knowledgeFileApi.ts`
- 新建：`frontend/src/pages/KnowledgeBaseLayout.tsx`
- 新建：`frontend/src/components/FileTable.tsx`
- 新建：`frontend/src/components/FileDetailDrawer.tsx`
- 新建：`frontend/src/components/KnowledgeBaseModal.tsx`
- 修改：`frontend/src/styles.css`

**要求：**

- 左侧展示知识库列表、创建、编辑、删除入口。
- 右侧展示当前知识库的文件管理区域。
- 文件列表支持文件名搜索、状态筛选。
- 上传只允许单文件。
- 前端限制 `.doc`、`.docx`、`.md`、`.markdown`、`.txt`。
- PDF 上传在前端提示当前版本不支持。
- 文件详情使用 Drawer 或 Modal。
- 文件删除需要确认弹窗。
- 重名上传错误展示后端返回信息。

**验证：**

```bash
cd frontend
npm run build
```

预期：TypeScript 和 Vite 构建通过。

---

## 任务 11：整体验证

**后端编译：**

```bash
cd backend
mvn -q -DskipTests compile
```

预期：所有后端模块编译通过。

**前端构建：**

```bash
cd frontend
npm run build
```

预期：前端构建通过。

**外部服务验证：**

只有在用户安装并启动 MySQL 和 MinIO 后执行：

```bash
cd backend
mvn spring-boot:run -pl kb-bootstrap
```

然后手工验证：

1. 创建知识库。
2. 上传 `.txt` 文件。
3. 文件列表展示该文件，状态为 `UPLOADED`。
4. 再次上传同名文件，提示“同一知识库下已存在同名文件，请先删除旧文件。”
5. 查看文件详情。
6. 下载文件。
7. 删除文件。
8. 文件未清空时删除知识库，删除被拒绝。
9. 文件清空后删除知识库，删除成功。

## 自检结果

- 已移除 Java 单元测试生成步骤。
- 已统一 Java 时间类型为 `LocalDateTime`。
- 已明确 Java 代码不使用 `var`。
- 已保留外部软件由用户安装的约束。

