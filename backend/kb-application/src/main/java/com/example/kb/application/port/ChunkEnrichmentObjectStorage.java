package com.example.kb.application.port;

import java.util.List;

public interface ChunkEnrichmentObjectStorage {

    StoredEnrichmentObject putEmbeddingText(PutEmbeddingTextCommand command);

    List<PutEmbeddingTextResult> putEmbeddingTexts(List<PutEmbeddingTextCommand> commands);

    void deleteEnrichmentsByFile(Long knowledgeBaseId, Long fileId);

    record PutEmbeddingTextCommand(
            Long knowledgeBaseId,
            Long fileId,
            Long chunkId,
            String content
    ) {
    }

    record StoredEnrichmentObject(
            String bucket,
            String objectKey
    ) {
    }

    record PutEmbeddingTextResult(
            Long chunkId,
            boolean success,
            StoredEnrichmentObject storedObject,
            String errorMessage
    ) {
    }
}
