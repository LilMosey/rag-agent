package com.example.kb.application.port;

import java.math.BigDecimal;
import java.util.List;

public interface RerankGenerator {

    RerankResult rerank(RerankCommand command);

    record RerankCommand(
            String query,
            List<RerankDocument> documents,
            Integer topN
    ) {
    }

    record RerankDocument(
            Long chunkId,
            String content
    ) {
    }

    record RerankResult(
            List<RerankItem> items,
            String provider,
            String model
    ) {
    }

    record RerankItem(
            Long chunkId,
            BigDecimal score,
            Integer rankNo
    ) {
    }
}
