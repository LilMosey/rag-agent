package com.example.kb.domain.model;

public record ChunkConfig(
        Long knowledgeBaseId,
        Long fileId,
        ChunkStrategy chunkStrategy,
        int chunkSize,
        int chunkOverlap
) {

    private static final ChunkStrategy DEFAULT_STRATEGY = ChunkStrategy.RECURSIVE;
    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 150;
    private static final int MIN_CHUNK_SIZE = 200;
    private static final int MAX_CHUNK_SIZE = 4000;
    private static final int MIN_CHUNK_OVERLAP = 0;
    private static final int MAX_CHUNK_OVERLAP = 1000;

    public static ChunkConfig normalize(
            Long knowledgeBaseId,
            Long fileId,
            ChunkStrategy chunkStrategy,
            Integer chunkSize,
            Integer chunkOverlap
    ) {
        ChunkStrategy normalizedStrategy = chunkStrategy == null ? DEFAULT_STRATEGY : chunkStrategy;
        int normalizedSize = chunkSize == null ? DEFAULT_CHUNK_SIZE : chunkSize;
        int normalizedOverlap = chunkOverlap == null ? DEFAULT_CHUNK_OVERLAP : chunkOverlap;
        if (normalizedSize < MIN_CHUNK_SIZE || normalizedSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException("chunkSize 必须在 200 到 4000 之间。");
        }
        if (normalizedOverlap < MIN_CHUNK_OVERLAP || normalizedOverlap > MAX_CHUNK_OVERLAP) {
            throw new IllegalArgumentException("chunkOverlap 必须在 0 到 1000 之间。");
        }
        if (normalizedOverlap >= normalizedSize) {
            throw new IllegalArgumentException("chunkOverlap 必须小于 chunkSize。");
        }
        return new ChunkConfig(knowledgeBaseId, fileId, normalizedStrategy, normalizedSize, normalizedOverlap);
    }
}
