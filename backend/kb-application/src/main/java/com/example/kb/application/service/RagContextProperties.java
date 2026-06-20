package com.example.kb.application.service;

public record RagContextProperties(
        Integer recentMessageLimit,
        Boolean reuseLastContextEnabled
) {

    public int safeRecentMessageLimit() {
        return recentMessageLimit == null ? 6 : recentMessageLimit;
    }

    public boolean isReuseLastContextEnabled() {
        return reuseLastContextEnabled == null || reuseLastContextEnabled;
    }
}
