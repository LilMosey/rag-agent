package com.example.kb.application.port;

import java.util.List;

public interface EmbeddingGenerator {

    GenerateEmbeddingsResult generate(GenerateEmbeddingsCommand command);

    record GenerateEmbeddingsCommand(
            List<String> texts
    ) {
    }

    record GenerateEmbeddingsResult(
            List<EmbeddingItem> items,
            String provider,
            String model,
            Integer totalTokens
    ) {
    }

    record EmbeddingItem(
            int textIndex,
            List<Float> vector
    ) {
    }
}
