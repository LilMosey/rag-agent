# 企业知识库文件管理设计

## 背景

本项目将从企业知识库文件管理系统开始建设。

第一版只聚焦“知识库空间”和“文件管理”，不实现登录、RAG 解析、向量写入、检索和 Agent 应用。

设计上会为后续 RAG 能力和基于 AgentScope Java 的 Agent 应用预留扩展边界。

## 目标

- 在当前工作目录下创建一个新的工程。
- 支持知识库管理。
- 支持单文件上传、列表、详情、下载、搜索、状态筛选和删除。
- 原始文件存储到 MinIO。
- 业务元数据存储到 MySQL。
- 为后续 Milvus 向量数据删除预留接口。
- 为后续 RAG 流程预留文件状态字段，但本期不实现解析和索引。
- 提供一个不需要登录的 React 管理台页面。

## 非目标

- 不做登录、用户、角色和权限。
- 不做 RAG 解析、切块、embedding、向量写入和检索。
- 不做 Agent 应用开发。
- 本期不支持 PDF 上传。
- 不做文件版本管理。
- 不做多级目录树。
- 不做批量上传。

## 技术栈

### 后端

- Java
- Spring Boot
- 兼容 AgentScope Java 的工程结构
- Maven 多模块工程
- MySQL
- MyBatis-Plus
- MinIO Java SDK
- Milvus 预留集成点
- REST API
- multipart 文件上传

AgentScope Java Spring Boot Starter 可以在后续开始 RAG 或 Agent 功能时再引入。
文件管理第一版不需要使用 AgentScope 的 Agent 运行逻辑。

### Java 代码约束

- `createdAt`、`updatedAt` 在 Java 代码中统一使用 `java.time.LocalDateTime`。
- Java 代码中不使用 `var`，局部变量也必须写明具体类型。
- 本期不生成 Java 单元测试代码，代码完成后由用户自行审阅。

### 前端

- React
- Vite
- TypeScript
- Ant Design
- npm 脚本：
  - `npm install`
  - `npm run start`

## 仓库结构

```text
backend/
  pom.xml
  kb-bootstrap/
  kb-api/
  kb-application/
  kb-domain/
  kb-infrastructure/
frontend/
```

### 后端模块职责

- `kb-bootstrap`：Spring Boot 启动入口、配置、依赖装配。
- `kb-api`：REST Controller、请求 DTO、响应 DTO。
- `kb-application`：应用服务和用例编排。
- `kb-domain`：领域模型、枚举、领域规则。
- `kb-infrastructure`：MySQL 持久化、MyBatis-Plus Mapper、MinIO 存储、Milvus 预留适配器。

建议依赖方向：

```text
kb-bootstrap -> kb-api -> kb-application -> kb-domain
kb-application -> 基础设施接口
kb-infrastructure -> kb-domain 和 application ports
```

`kb-domain` 不依赖 Spring、MyBatis-Plus、MinIO 或 Milvus。

## 功能范围

### 知识库管理

第一版支持：

- 创建知识库。
- 查询知识库列表。
- 查看知识库详情。
- 修改知识库名称和描述。
- 删除空知识库。

删除规则：

- 如果知识库下仍然存在文件，则拒绝删除。
- 用户需要先删除知识库下的所有文件。

### 文件管理

第一版支持：

- 一次上传一个文件。
- 查询某个知识库下的文件列表。
- 按文件名搜索。
- 按文件状态筛选。
- 查看文件详情。
- 下载文件。
- 硬删除文件。

规则：

- 文件平铺在知识库下。
- 不做多级目录树。
- 同一个知识库下不允许出现同名文件。
- 不做文件版本管理。
- 如果文件名已经存在，上传失败。
- 用户必须先删除已有文件，才能上传同名新文件。

### 支持的文件类型

本期允许上传：

- Word：`.doc`、`.docx`
- Markdown：`.md`、`.markdown`
- 文本：`.txt`

后续预留：

- PDF

本期上传 PDF 时必须拒绝，但数据模型和文档需要保留后续支持 PDF 的空间。

## 文件状态

第一版保存文件状态，用于后续 RAG 流程扩展。

```text
UPLOADED
PENDING_PARSE
PARSING
PARSE_FAILED
READY
DISABLED
```

本期上传成功后，可以将文件记录状态置为 `UPLOADED`。
后续 RAG 功能可以引入后台解析任务，再推进这些状态。

## 数据模型

### `knowledge_base`

建议字段：

- `id`
- `name`
- `description`
- `created_at`，Java 类型为 `LocalDateTime`
- `updated_at`，Java 类型为 `LocalDateTime`

### `knowledge_file`

建议字段：

- `id`
- `knowledge_base_id`
- `original_filename`
- `file_ext`
- `content_type`
- `file_size`
- `checksum_sha256`
- `storage_bucket`
- `storage_object_key`
- `file_type`
- `file_status`
- `parse_error`
- `created_at`，Java 类型为 `LocalDateTime`
- `updated_at`，Java 类型为 `LocalDateTime`

建议约束：

- `knowledge_base_id + original_filename` 唯一索引。
- `knowledge_base_id` 普通索引。
- `file_status` 普通索引。

## 存储设计

### MySQL

MySQL 存储知识库元数据和文件元数据。

MySQL 不存储原始文件二进制内容。

### MinIO

MinIO 存储原始文件。

建议 object key 格式：

```text
knowledge-bases/{knowledgeBaseId}/files/{fileId}/{originalFilename}
```

实际实现时可以调整 object key，但应该保证稳定，并且不要只依赖原始文件名。

### Milvus

第一版不使用 Milvus。

后端预留向量清理接口，例如：

```text
VectorIndexCleaner.deleteByFileId(fileId)
```

第一版实现可以是空实现。后续接入 RAG 向量写入后，该实现负责删除某个文件对应的全部向量记录。

## 删除行为

文件删除采用硬删除。

应用服务层按以下顺序编排：

1. 调用预留的 Milvus/vector 清理适配器。
2. 删除 MinIO 中的原始文件。
3. 删除 MySQL 中的文件元数据。

因为本期不会写入向量数据，所以 vector 清理适配器可以先做空实现。

知识库删除：

1. 检查知识库下是否存在文件。
2. 如果存在文件，拒绝删除。
3. 如果不存在文件，删除 MySQL 中的知识库记录。

## REST API

### 知识库接口

```text
POST   /api/knowledge-bases
GET    /api/knowledge-bases
GET    /api/knowledge-bases/{id}
PUT    /api/knowledge-bases/{id}
DELETE /api/knowledge-bases/{id}
```

### 文件接口

```text
POST   /api/knowledge-bases/{kbId}/files
GET    /api/knowledge-bases/{kbId}/files
GET    /api/knowledge-bases/{kbId}/files/{fileId}
GET    /api/knowledge-bases/{kbId}/files/{fileId}/download
DELETE /api/knowledge-bases/{kbId}/files/{fileId}
```

文件上传使用 `multipart/form-data`。

文件列表接口支持：

- 文件名关键字。
- 文件状态筛选。
- 分页。

## 前端设计

前端是一个单页管理台。

布局：

- 左侧：知识库列表、创建按钮、编辑/删除入口。
- 右侧：当前选中知识库的文件管理区域。

右侧顶部区域：

- 知识库名称和描述。
- 上传按钮。
- 文件名搜索框。
- 状态筛选。

文件表格列：

- 文件名
- 类型
- 大小
- 状态
- 创建时间
- 操作

文件操作：

- 详情
- 下载
- 删除

文件详情使用 Ant Design Drawer 或 Modal 展示，包含：

- 原始文件名
- 文件类型
- MIME 类型
- 文件大小
- SHA256 校验值
- MinIO bucket
- MinIO object key
- 文件状态
- 解析错误信息，如果存在
- 创建时间
- 更新时间

空状态：

- 没有知识库。
- 当前知识库下没有文件。
- 当前搜索或筛选条件下没有匹配文件。

上传行为：

- 一次只能选择一个文件。
- 前端先校验文件扩展名。
- 后端再次校验文件扩展名。
- 重名上传错误需要清晰提示：
  "同一知识库下已存在同名文件，请先删除旧文件。"

## 外部软件安装确认清单

当前电脑没有安装 MySQL、Milvus 和 MinIO。
这些都是外部依赖，需要由用户确认后自行安装或启动。

除非用户明确要求，否则 Codex 不自动安装这些外部软件。

### MySQL

用户确认后执行的步骤：

1. 安装 MySQL。
2. 创建项目数据库，例如 `enterprise_kb`。
3. 创建应用访问使用的用户名和密码。
4. 确认 host、port、database name、username、password。
5. 将这些配置提供给后端使用。

建议配置项：

```text
spring.datasource.url
spring.datasource.username
spring.datasource.password
```

### MinIO

用户确认后执行的步骤：

1. 安装或启动 MinIO。
2. 创建 access key 和 secret key。
3. 创建原始文件存储 bucket，例如 `enterprise-kb-files`。
4. 确认 endpoint、bucket name、access key、secret key。
5. 将这些配置提供给后端使用。

建议配置项：

```text
app.storage.minio.endpoint
app.storage.minio.bucket
app.storage.minio.access-key
app.storage.minio.secret-key
```

### Milvus

后续 RAG 阶段由用户确认后执行的步骤：

1. 安装或启动 Milvus。
2. 确认 host 和 port。
3. 确认 collection 命名策略。
4. 在实现向量索引时，将这些配置提供给后端使用。

如果第一版的 vector 清理实现保持为空实现，则文件管理第一版不强依赖 Milvus。

## 本地开发说明

前端：

```text
cd frontend
npm install
npm run start
```

后端：

Maven 模块创建完成后的预期启动命令：

```text
cd backend
mvn spring-boot:run -pl kb-bootstrap
```

外部软件的安装和启动由用户控制。

## 实现顺序建议

建议实现顺序：

1. 创建后端 Maven 多模块骨架。
2. 创建前端 Vite React TypeScript 骨架。
3. 添加后端领域模型和应用层端口。
4. 添加 MySQL 表结构和 MyBatis-Plus 持久化。
5. 添加 MinIO 存储适配器。
6. 添加 REST API。
7. 添加前端知识库管理布局。
8. 添加前端文件列表、上传、详情、下载和删除。
9. 添加校验和错误处理。
10. 添加 README，说明 MySQL、MinIO 和可选 Milvus 的安装与配置步骤。

## 实现前确认清单

开始创建脚手架或实现代码前，需要确认以下事项：

- Java 版本：建议使用 Java 21，除非选定的 AgentScope Java starter 版本要求其他基线。
- Spring Boot 版本：创建 Maven parent POM 前，需要按当前 AgentScope Java Spring Boot Starter 的兼容性确认。
- 外部软件安装：MySQL、MinIO 和 Milvus 由用户安装或启动。除非用户明确要求，Codex 只编写配置和文档，不执行安装命令。
- Docker Compose 示例：可选。如果添加，只作为示例文件；没有用户明确指令时，Codex 不执行。
- 默认最大文件大小：第一版建议 100 MB。
- `.doc` 和 `.docx` 本期只作为允许上传的文件类型。实际解析行为属于后续 RAG 工作。
