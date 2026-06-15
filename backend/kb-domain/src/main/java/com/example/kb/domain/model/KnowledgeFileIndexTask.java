package com.example.kb.domain.model;

import java.time.LocalDateTime;

public record KnowledgeFileIndexTask(
        Long id,
        Long knowledgeBaseId,
        Long fileId,
        IndexTaskStatus status,
        int retryCount,
        int maxRetry,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
