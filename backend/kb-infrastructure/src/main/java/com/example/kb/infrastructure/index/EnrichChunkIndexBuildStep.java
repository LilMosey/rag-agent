package com.example.kb.infrastructure.index;

import com.example.kb.application.service.ChunkEnrichmentService;
import com.example.kb.domain.model.DocumentChunk;
import com.example.kb.domain.model.KnowledgeFile;
import com.example.kb.domain.model.KnowledgeFileIndexTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(40)
public class EnrichChunkIndexBuildStep implements IndexBuildStep {

    private static final Logger log = LoggerFactory.getLogger(EnrichChunkIndexBuildStep.class);

    private final ChunkEnrichmentService chunkEnrichmentService;

    public EnrichChunkIndexBuildStep(ChunkEnrichmentService chunkEnrichmentService) {
        this.chunkEnrichmentService = chunkEnrichmentService;
    }

    @Override
    public String name() {
        return "ENRICH_CHUNK";
    }

    @Override
    public void execute(IndexBuildContext context) {
        KnowledgeFileIndexTask task = context.task();
        KnowledgeFile file = context.file();
        List<DocumentChunk> chunks = context.chunks();
        log.info("索引步骤执行入参: step={}, taskId={}, fileId={}, chunkCount={}",
                name(), task.id(), file.id(), chunks.size());
        chunkEnrichmentService.rebuildEnrichments(file, chunks);
        log.info("索引步骤执行出参: step={}, taskId={}, fileId={}, chunkCount={}",
                name(), task.id(), file.id(), chunks.size());
    }
}
