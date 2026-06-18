package com.example.kb.domain.model;

public enum ChunkStrategy {
    FIXED_SIZE("固定长度分块"),
    SECTION("按章节分块"),
    RECURSIVE("递归分块");

    private final String logName;

    ChunkStrategy(String logName) {
        this.logName = logName;
    }

    public String logName() {
        return logName;
    }
}
