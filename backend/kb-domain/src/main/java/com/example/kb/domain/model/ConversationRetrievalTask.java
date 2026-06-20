package com.example.kb.domain.model;

import java.time.LocalDateTime;

public record ConversationRetrievalTask(
        Long id,
        Long conversationRetrievalId,
        RetrievalTaskType taskType,
        String queryText,
        RetrievalTaskStatus status,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
