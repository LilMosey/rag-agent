package com.example.kb.application.service;

public record RagRetrievalProperties(
        Boolean queryRewriteEnabled,
        Boolean hydeEnabled,
        Boolean multiQueryEnabled,
        Boolean bm25Enabled,
        Integer queryRewriteHistoryMessageLimit,
        Integer multiQueryCount,
        Integer denseTopK,
        Integer bm25TopK,
        Integer fusionTopK,
        Integer contextTopK,
        Integer rrfK,
        Integer executorCoreSize,
        Integer executorMaxSize,
        Integer executorQueueCapacity
) {

    public boolean isQueryRewriteEnabled() {
        return queryRewriteEnabled == null || queryRewriteEnabled;
    }

    public boolean isHydeEnabled() {
        return hydeEnabled == null || hydeEnabled;
    }

    public boolean isMultiQueryEnabled() {
        return multiQueryEnabled == null || multiQueryEnabled;
    }

    public boolean isBm25Enabled() {
        return bm25Enabled != null && bm25Enabled;
    }

    public int safeQueryRewriteHistoryMessageLimit() {
        return queryRewriteHistoryMessageLimit == null ? 6 : queryRewriteHistoryMessageLimit;
    }

    public int safeMultiQueryCount() {
        return multiQueryCount == null ? 3 : multiQueryCount;
    }

    public int safeDenseTopK() {
        return denseTopK == null ? 10 : denseTopK;
    }

    public int safeBm25TopK() {
        return bm25TopK == null ? 10 : bm25TopK;
    }

    public int safeFusionTopK() {
        return fusionTopK == null ? 10 : fusionTopK;
    }

    public int safeContextTopK() {
        return contextTopK == null ? 5 : contextTopK;
    }

    public int safeRrfK() {
        return rrfK == null ? 60 : rrfK;
    }

    public int safeExecutorCoreSize() {
        return executorCoreSize == null ? 4 : executorCoreSize;
    }

    public int safeExecutorMaxSize() {
        return executorMaxSize == null ? 8 : executorMaxSize;
    }

    public int safeExecutorQueueCapacity() {
        return executorQueueCapacity == null ? 100 : executorQueueCapacity;
    }
}
