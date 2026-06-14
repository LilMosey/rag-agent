package com.example.kb.api.dto;

import com.example.kb.domain.model.FileStatus;
import com.example.kb.domain.model.FileType;
import com.example.kb.domain.model.KnowledgeFile;

import java.time.LocalDateTime;

public final class KnowledgeFileDtos {

    private KnowledgeFileDtos() {
    }

    public record Response(
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
            String parseError,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {

        public static Response from(KnowledgeFile file) {
            return new Response(
                    file.id(),
                    file.knowledgeBaseId(),
                    file.originalFilename(),
                    file.fileExt(),
                    file.contentType(),
                    file.fileSize(),
                    file.checksumSha256(),
                    file.storageBucket(),
                    file.storageObjectKey(),
                    file.fileType(),
                    file.fileStatus(),
                    file.parseError(),
                    file.createdAt(),
                    file.updatedAt()
            );
        }
    }
}
