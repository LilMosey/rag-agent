package com.example.kb.infrastructure.chunk;

import com.example.kb.application.port.DocumentChunker;
import com.example.kb.domain.model.ChunkConfig;
import com.example.kb.domain.model.ParsedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultDocumentChunker implements DocumentChunker {

    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentChunker.class);

    private final FixedSizeChunkStrategy fixedSizeChunkStrategy;
    private final SectionChunkStrategy sectionChunkStrategy;
    private final RecursiveChunkStrategy recursiveChunkStrategy;

    public DefaultDocumentChunker() {
        ChunkContentBuilder contentBuilder = new ChunkContentBuilder();
        this.fixedSizeChunkStrategy = new FixedSizeChunkStrategy(contentBuilder);
        this.sectionChunkStrategy = new SectionChunkStrategy(contentBuilder);
        this.recursiveChunkStrategy = new RecursiveChunkStrategy(contentBuilder);
    }

    @Override
    public List<DocumentChunker.ChunkDraft> chunk(ParsedDocument document, ChunkConfig chunkConfig) {
        log.info("文档分块入参: knowledgeBaseId={}, fileId={}, filename={}, strategy={}, chunkSize={}, chunkOverlap={}, sectionCount={}",
                document.knowledgeBaseId(), document.fileId(), document.filename(), chunkConfig.chunkStrategy().logName(),
                chunkConfig.chunkSize(), chunkConfig.chunkOverlap(), document.sections() == null ? 0 : document.sections().size());
        try {
            List<DocumentChunker.ChunkDraft> drafts = switch (chunkConfig.chunkStrategy()) {
                case FIXED_SIZE -> {
                    log.info("文档分块分支: strategy={}, fileId={}", chunkConfig.chunkStrategy().logName(), document.fileId());
                    yield fixedSizeChunkStrategy.chunk(document, chunkConfig);
                }
                case SECTION -> {
                    log.info("文档分块分支: strategy={}, fileId={}", chunkConfig.chunkStrategy().logName(), document.fileId());
                    yield sectionChunkStrategy.chunk(document, chunkConfig);
                }
                case RECURSIVE -> {
                    log.info("文档分块分支: strategy={}, fileId={}", chunkConfig.chunkStrategy().logName(), document.fileId());
                    yield recursiveChunkStrategy.chunk(document, chunkConfig);
                }
            };
            log.info("文档分块出参: fileId={}, strategy={}, chunkCount={}",
                    document.fileId(), chunkConfig.chunkStrategy().logName(), drafts.size());
            return drafts;
        } catch (Exception exception) {
            log.error("文档分块异常: fileId={}, filename={}, strategy={}",
                    document.fileId(), document.filename(), chunkConfig.chunkStrategy().logName(), exception);
            throw new IllegalStateException("文档分块失败: " + exception.getMessage(), exception);
        }
    }
}
