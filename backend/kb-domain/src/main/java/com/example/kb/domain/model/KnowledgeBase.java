package com.example.kb.domain.model;

import java.time.LocalDateTime;

public record KnowledgeBase(
        Long id,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
