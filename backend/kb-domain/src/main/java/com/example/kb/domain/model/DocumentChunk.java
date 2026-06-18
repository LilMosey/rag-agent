package com.example.kb.domain.model;

import java.time.LocalDateTime;

public record DocumentChunk(
        Long id,
        Long knowledgeBaseId,
        Long fileId,
        String sectionId,
        String parentSectionId,
        int chunkIndex,
        ChunkStrategy chunkStrategy,
        int chunkSize,
        int chunkOverlap,
        String titlePath,
        String contentPreview,
        String contentHash,
        int contentSize,
        Integer startOffset,
        Integer endOffset,
        String storageBucket,
        String storageObjectKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
