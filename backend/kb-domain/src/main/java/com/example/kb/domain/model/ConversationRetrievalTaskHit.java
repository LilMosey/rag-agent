package com.example.kb.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ConversationRetrievalTaskHit(
        Long id,
        Long retrievalTaskId,
        Long knowledgeBaseId,
        Long fileId,
        Long chunkId,
        Integer chunkIndex,
        BigDecimal score,
        Integer rankNo,
        LocalDateTime createdAt
) {
}
