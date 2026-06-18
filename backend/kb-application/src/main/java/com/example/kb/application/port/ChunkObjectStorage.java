package com.example.kb.application.port;

import java.util.List;

public interface ChunkObjectStorage {

    StoredChunkObject putChunk(PutChunkCommand command);

    List<StoredChunkObject> putChunks(List<PutChunkCommand> commands);

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
