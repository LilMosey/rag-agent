package com.example.kb.application.port;

public interface ChunkObjectStorage {

    StoredChunkObject putChunk(PutChunkCommand command);

    void deleteChunksByFile(Long knowledgeBaseId, Long fileId);

    record PutChunkCommand(
            Long knowledgeBaseId,
            Long fileId,
            int chunkIndex,
            String contentHash,
            String content
    ) {
    }

    record StoredChunkObject(
            String bucket,
            String objectKey
    ) {
    }
}
