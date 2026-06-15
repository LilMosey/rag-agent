package com.example.kb.application.port;

import com.example.kb.domain.model.KnowledgeFileIndexTask;

public interface IndexPipeline {

    IndexPipelineResult execute(KnowledgeFileIndexTask task);

    record IndexPipelineResult(
            boolean success,
            boolean skipped,
            String message
    ) {

        public static IndexPipelineResult skipped(String message) {
            return new IndexPipelineResult(false, true, message);
        }

        public static IndexPipelineResult success(String message) {
            return new IndexPipelineResult(true, false, message);
        }

        public static IndexPipelineResult failed(String message) {
            return new IndexPipelineResult(false, false, message);
        }
    }
}
