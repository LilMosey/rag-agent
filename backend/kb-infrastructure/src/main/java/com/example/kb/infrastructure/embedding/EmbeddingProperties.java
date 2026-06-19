package com.example.kb.infrastructure.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.embedding")
public record EmbeddingProperties(
        boolean enabled,
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        int dimension,
        int batchSize
) {
}
