package com.example.kb.application.port;

import com.example.kb.domain.model.IndexTaskStatus;
import com.example.kb.domain.model.KnowledgeFileIndexTask;

import java.time.LocalDateTime;
import java.util.List;

public interface KnowledgeFileIndexTaskRepository {

    KnowledgeFileIndexTask save(KnowledgeFileIndexTask task);

    List<KnowledgeFileIndexTask> findPending(int limit);

    boolean markRunning(Long taskId, LocalDateTime startedAt, LocalDateTime updatedAt);

    void markFinished(
            Long taskId,
            IndexTaskStatus status,
            String errorMessage,
            LocalDateTime finishedAt,
            LocalDateTime updatedAt
    );
}
