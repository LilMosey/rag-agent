# 文档清洗层设计

## 1. 背景

当前项目已经完成文件上传、索引任务、文档解析层。解析层会把 TXT、Markdown、DOCX 文件统一转换为 `ParsedDocument / DocumentSection`。下一步进入 chunk 前，需要增加一个轻量清洗层，把解析结果中的格式噪声、不可见字符和过度空白处理掉。

清洗层的位置如下：

```text
原始文件
  -> Parser
  -> ParsedDocument
  -> DocumentCleaner
  -> ParsedDocument
  -> Chunker
```

本阶段只设计清洗层，不做 chunk、embedding、Milvus、query、rerank、摘要和 HyDE。

## 2. 设计目标

清洗层第一版目标是“保守、稳定、少破坏原文”。

具体目标：

1. 统一不同解析器输出中的基础文本格式。
2. 减少不可见字符、多余空行、多余空白对 chunk 的干扰。
3. 删除完全无内容的空章节。
4. 对 Markdown 图片和链接做可读文本转换。
5. 保护代码块，不破坏缩进、换行和符号。
6. 保持 `ParsedDocument / DocumentSection` 结构不变，方便后续 chunk 直接使用。

## 3. 本阶段不做的内容

本阶段不做以下内容：

1. 不做 chunk 拆分。
2. 不做 embedding。
3. 不做 Milvus 写入。
4. 不做 chunk 摘要。
5. 不做 HyDE。
6. 不做 Doc2Query。
7. 不做语义去重。
8. 不做自动摘要或改写。
9. 不删除疑似重复段落。
10. 不做敏感词处理。

## 4. 输入输出

清洗层输入：

```text
ParsedDocument
```

清洗层输出：

```text
ParsedDocument
```

第一版不新增 `CleanedDocument` 模型。原因是：

1. 当前 demo 阶段不需要额外模型层。
2. 清洗不改变文档语义结构，只处理文本质量。
3. 后续 chunker 可以继续依赖同一个 `ParsedDocument`。
4. 如果未来需要保留清洗前后差异，再单独扩展清洗快照或版本字段。

## 5. 清洗规则

### 5.1 文档级规则

文档级规则：

1. `title` 去除首尾空白。
2. `metadata` 原样保留。
3. `sections` 按原始 `orderIndex` 顺序处理。
4. 清洗后删除完全空章节。
5. 不改变 `knowledgeBaseId`、`fileId`、`filename`、`fileType`。

完全空章节定义：

```text
title 为空
content 为空
且没有必要保留的 metadata
```

如果章节有标题但正文为空，第一版保留。原因是标题本身可能是后续 chunk 的上下文。

### 5.2 Section 标题规则

标题清洗规则：

1. 去除首尾空白。
2. 合并标题内部连续空白为一个空格。
3. 不删除标题中的业务符号，例如 `API-v2`、`Q&A`、`A/B 测试`。

### 5.3 Section 正文规则

正文清洗规则：

1. 统一换行：`\r\n`、`\r` 转为 `\n`。
2. 去掉不可见控制字符，但保留 `\n` 和 `\t`。
3. 去除首尾空白。
4. 连续 3 个及以上空行压缩为 2 个空行。
5. 普通文本行尾多余空格删除。
6. 不删除正文中的业务内容。
7. 不做同义词替换。
8. 不做内容改写。

## 6. Markdown 特殊规则

Markdown 清洗只处理已经进入 `DocumentSection.content` 的文本，不重新解析章节结构。

### 6.1 图片

Markdown 图片保留占位。

规则：

```text
![架构图](xxx.png) -> [图片: 架构图]
![](xxx.png)      -> [图片]
```

原因：

1. 图片可能承载业务含义。
2. 当前阶段不做 OCR 和多模态理解。
3. 保留占位能提醒后续检索和展示“这里原本有图片”。

### 6.2 链接

Markdown 链接转为可读文本。

规则：

```text
[安装文档](https://example.com) -> 安装文档（https://example.com）
```

如果链接文本为空：

```text
[](https://example.com) -> https://example.com
```

原因：

1. 保留链接文本有利于 embedding。
2. 保留 URL 有利于溯源和人工排查。

### 6.3 代码块

代码块必须保护。

规则：

1. fenced code block 内部不做行内 Markdown 转换。
2. 不压缩代码缩进。
3. 不删除代码中的符号。
4. 代码块首尾可以 trim 多余空行，但不改变内部结构。

示例：

```text
```java
public class Demo {
    void run() {
    }
}
```
```

清洗后仍保留代码块结构。

## 7. 表格文本规则

当前 DOCX 解析器已经把表格转成纯文本行：

```text
字段 A | 字段 B | 字段 C
值 A | 值 B | 值 C
```

清洗层只做轻度规整：

1. 去除单元格文本首尾空白。
2. 把多余的 ` |  | ` 保留，不强行删除空列。
3. 不重构表格。
4. 不转 JSON。
5. 不做表头识别。

原因是表格的结构化还原更适合放到后续更强的解析层，而不是清洗层。

## 8. title_path 规则

第一版不把 `title_path` 注入 `content`。

继续保留在 section metadata 中：

```text
metadata.title_path = 产品手册 > 部署说明 > Docker
```

原因：

1. 是否把标题路径拼进 embedding 文本，应该由 chunk 阶段决定。
2. 有些 embedding 场景拼标题会提升召回。
3. 有些场景标题词会过度影响相似度。
4. 清洗层只负责文本标准化，不决定 embedding 输入策略。

## 9. 空章节处理

清洗后章节分为三类：

1. 有标题、有正文：保留。
2. 有标题、无正文：保留。
3. 无标题、无正文：删除。

暂不合并超短章节。超短章节是否合并，由 chunk 阶段处理。

## 10. 清洗失败处理

清洗失败属于索引任务失败。

建议处理方式：

1. 记录完整异常堆栈。
2. `knowledge_file.file_status` 更新为 `PARSE_FAILED` 或后续新增 `CLEAN_FAILED`。
3. `knowledge_file.parse_error` 保存简短错误原因。
4. `knowledge_file_index_task.status` 更新为 `FAILED`。
5. `knowledge_file_index_task.error_message` 保存简短错误原因。

第一版可以继续复用 `PARSE_FAILED`，后续如果状态需要更细，再扩展 `CLEAN_FAILED`。

## 11. 与摘要、HyDE 的关系

清洗层不做摘要，也不做 HyDE。

后续链路建议如下：

```text
ParsedDocument
  -> Cleaner
  -> Chunker
  -> 可选 Chunk Summary
  -> Embedding
  -> Store
```

HyDE 属于在线查询阶段：

```text
用户问题
  -> HyDE 生成假设答案
  -> Query Embedding
  -> Vector Recall
```

Doc2Query 属于离线索引增强，位置在 chunk 后、embedding 前：

```text
Chunk
  -> 生成可能问题
  -> 问题向量入库或关联 chunk
```

## 12. 推荐结论

第一版清洗层采用保守策略：

1. 输入输出都使用 `ParsedDocument`。
2. 只做确定性的文本标准化。
3. Markdown 图片保留占位。
4. Markdown 链接转为可读文本。
5. 代码块保护。
6. 不把 `title_path` 注入正文。
7. 不合并超短章节。
8. 不做摘要、HyDE、Doc2Query。

这样能让后续 chunk 输入更干净，同时避免清洗阶段过早影响语义。
