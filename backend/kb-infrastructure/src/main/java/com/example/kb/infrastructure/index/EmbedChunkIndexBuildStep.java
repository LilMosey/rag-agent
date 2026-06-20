package com.example.kb.infrastructure.index;

import com.example.kb.application.service.ChunkEmbeddingService;
import com.example.kb.domain.model.DocumentChunk;
import com.example.kb.domain.model.KnowledgeFile;
import com.example.kb.domain.model.KnowledgeFileIndexTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(50)
public class EmbedChunkIndexBuildStep implements IndexBuildStep {

    private static final Logger log = LoggerFactory.getLogger(EmbedChunkIndexBuildStep.class);

    private final ChunkEmbeddingService chunkEmbeddingService;

    public EmbedChunkIndexBuildStep(ChunkEmbeddingService chunkEmbeddingService) {
        this.chunkEmbeddingService = chunkEmbeddingService;
    }

    @Override
    public String name() {
        return "EMBED_CHUNK";
    }

    @Override
    public void execute(IndexBuildContext context) {
        KnowledgeFileIndexTask task = context.task();
        KnowledgeFile file = context.file();
        List<DocumentChunk> chunks = context.chunks();
        log.info("索引步骤执行入参: step={}, taskId={}, fileId={}, chunkCount={}",
                name(), task.id(), file.id(), chunks.size());
        chunkEmbeddingService.rebuildEmbeddings(file, chunks);
        log.info("索引步骤执行出参: step={}, taskId={}, fileId={}, chunkCount={}",
                name(), task.id(), file.id(), chunks.size());
    }
}
