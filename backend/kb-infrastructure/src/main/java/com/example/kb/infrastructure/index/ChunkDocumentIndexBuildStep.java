package com.example.kb.infrastructure.index;

import com.example.kb.application.service.DocumentChunkService;
import com.example.kb.domain.model.DocumentChunk;
import com.example.kb.domain.model.KnowledgeFile;
import com.example.kb.domain.model.KnowledgeFileIndexTask;
import com.example.kb.domain.model.ParsedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(30)
public class ChunkDocumentIndexBuildStep implements IndexBuildStep {

    private static final Logger log = LoggerFactory.getLogger(ChunkDocumentIndexBuildStep.class);

    private final DocumentChunkService documentChunkService;

    public ChunkDocumentIndexBuildStep(DocumentChunkService documentChunkService) {
        this.documentChunkService = documentChunkService;
    }

    @Override
    public String name() {
        return "CHUNK_DOCUMENT";
    }

    @Override
    public void execute(IndexBuildContext context) {
        KnowledgeFileIndexTask task = context.task();
        KnowledgeFile file = context.file();
        ParsedDocument cleanedDocument = context.cleanedDocument();
        if (cleanedDocument == null) {
            throw new IllegalStateException("文档切分步骤缺少 cleanedDocument。");
        }
        log.info("索引步骤执行入参: step={}, taskId={}, fileId={}, sectionCount={}",
                name(), task.id(), file.id(), cleanedDocument.sections().size());
        List<DocumentChunk> chunks = documentChunkService.rebuildChunks(file, cleanedDocument);
        context.chunks(chunks);
        log.info("索引步骤执行出参: step={}, taskId={}, fileId={}, chunkCount={}",
                name(), task.id(), file.id(), chunks.size());
    }
}
