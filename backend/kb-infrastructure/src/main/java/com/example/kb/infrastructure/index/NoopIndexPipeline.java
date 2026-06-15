package com.example.kb.infrastructure.index;

import com.example.kb.application.port.IndexPipeline;
import com.example.kb.domain.model.KnowledgeFileIndexTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopIndexPipeline implements IndexPipeline {

    private static final Logger log = LoggerFactory.getLogger(NoopIndexPipeline.class);

    @Override
    public IndexPipeline.IndexPipelineResult execute(KnowledgeFileIndexTask task) {
        log.info("索引 Pipeline 入参: taskId={}, fileId={}, knowledgeBaseId={}", task.id(), task.fileId(), task.knowledgeBaseId());
        log.info("索引 Pipeline 分支: 当前阶段未实现 parse/clean/chunk/embed/store, taskId={}", task.id());
        IndexPipeline.IndexPipelineResult result = IndexPipeline.IndexPipelineResult.skipped("索引 Pipeline 尚未实现，当前任务跳过。");
        log.info("索引 Pipeline 出参: taskId={}, skipped={}, message={}", task.id(), result.skipped(), result.message());
        return result;
    }
}
