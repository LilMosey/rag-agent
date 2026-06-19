package com.example.kb.application.port;

import com.example.kb.domain.model.ChunkEnrichment;

import java.util.List;

public interface ChunkEnrichmentRepository {

    void deleteByFileId(Long fileId);

    ChunkEnrichment save(ChunkEnrichment chunkEnrichment);

    List<ChunkEnrichment> saveBatch(List<ChunkEnrichment> chunkEnrichments);

    List<ChunkEnrichment> findByFileId(Long fileId);
}
