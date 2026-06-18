package com.example.kb.domain.model;

import java.time.LocalDateTime;

public record KnowledgeFile(
        Long id,
        Long knowledgeBaseId,
        String originalFilename,
        String fileExt,
        String contentType,
        long fileSize,
        String checksumSha256,
        String storageBucket,
        String storageObjectKey,
        FileType fileType,
        FileStatus fileStatus,
        ChunkStrategy chunkStrategy,
        int chunkSize,
        int chunkOverlap,
        String parseError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
