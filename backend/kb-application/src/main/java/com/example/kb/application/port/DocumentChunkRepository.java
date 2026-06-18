package com.example.kb.application.port;

import com.example.kb.domain.model.DocumentChunk;

import java.util.List;

public interface DocumentChunkRepository {

    void deleteByFileId(Long fileId);

    DocumentChunk save(DocumentChunk chunk);

    List<DocumentChunk> findByFileId(Long fileId);
}
