package com.example.kb.api.dto;

import com.example.kb.domain.model.KnowledgeBase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class KnowledgeBaseDtos {

    private KnowledgeBaseDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "知识库名称不能为空。")
            @Size(max = 128, message = "知识库名称不能超过 128 个字符。")
            String name,

            @Size(max = 1024, message = "知识库描述不能超过 1024 个字符。")
            String description
    ) {
    }

    public record UpdateRequest(
            @NotBlank(message = "知识库名称不能为空。")
            @Size(max = 128, message = "知识库名称不能超过 128 个字符。")
            String name,

            @Size(max = 1024, message = "知识库描述不能超过 1024 个字符。")
            String description
    ) {
    }

    public record Response(
            Long id,
            String name,
            String description,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {

        public static Response from(KnowledgeBase knowledgeBase) {
            return new Response(
                    knowledgeBase.id(),
                    knowledgeBase.name(),
                    knowledgeBase.description(),
                    knowledgeBase.createdAt(),
                    knowledgeBase.updatedAt()
            );
        }
    }
}
