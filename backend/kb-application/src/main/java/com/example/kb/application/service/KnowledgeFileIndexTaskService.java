package com.example.kb.application.service;

import com.example.kb.application.port.IndexPipeline;
import com.example.kb.application.port.KnowledgeFileIndexTaskRepository;
import com.example.kb.domain.model.IndexTaskStatus;
import com.example.kb.domain.model.KnowledgeFileIndexTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

public class KnowledgeFileIndexTaskService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFileIndexTaskService.class);
    private static final int DEFAULT_MAX_RETRY = 3;

    private final KnowledgeFileIndexTaskRepository taskRepository;
    private final IndexPipeline indexPipeline;

    public KnowledgeFileIndexTaskService(
            KnowledgeFileIndexTaskRepository taskRepository,
            IndexPipeline indexPipeline
    ) {
        this.taskRepository = taskRepository;
        this.indexPipeline = indexPipeline;
    }

    public KnowledgeFileIndexTask createPendingTask(Long knowledgeBaseId, Long fileId) {
        log.info("创建索引任务入参: knowledgeBaseId={}, fileId={}", knowledgeBaseId, fileId);
        LocalDateTime now = LocalDateTime.now();
        KnowledgeFileIndexTask task = new KnowledgeFileIndexTask(
                null,
                knowledgeBaseId,
                fileId,
                IndexTaskStatus.PENDING,
                0,
                DEFAULT_MAX_RETRY,
                null,
                null,
                null,
                now,
                now
        );
        KnowledgeFileIndexTask saved = taskRepository.save(task);
        log.info("创建索引任务出参: taskId={}, status={}", saved.id(), saved.status());
        return saved;
    }

    public void processPendingTasks(int limit) {
        log.info("扫描待处理索引任务入参: limit={}", limit);
        List<KnowledgeFileIndexTask> tasks = taskRepository.findPending(limit);
        log.info("扫描待处理索引任务出参: count={}", tasks.size());
        for (KnowledgeFileIndexTask task : tasks) {
            processOne(task);
        }
    }

    private void processOne(KnowledgeFileIndexTask task) {
        log.info("处理索引任务入参: taskId={}, fileId={}, status={}", task.id(), task.fileId(), task.status());
        LocalDateTime startedAt = LocalDateTime.now();
        boolean acquired = taskRepository.markRunning(task.id(), startedAt, startedAt);
        if (acquired) {
            log.info("处理索引任务分支: 领取成功, taskId={}", task.id());
        } else {
            log.warn("处理索引任务分支: 领取失败, taskId={}", task.id());
            return;
        }

        try {
            IndexPipeline.IndexPipelineResult result = indexPipeline.execute(task);
            IndexTaskStatus finalStatus = resolveFinalStatus(result);
            LocalDateTime finishedAt = LocalDateTime.now();
            taskRepository.markFinished(task.id(), finalStatus, result.message(), finishedAt, finishedAt);
            log.info("处理索引任务出参: taskId={}, finalStatus={}, message={}", task.id(), finalStatus, result.message());
        } catch (Exception exception) {
            LocalDateTime finishedAt = LocalDateTime.now();
            taskRepository.markFinished(task.id(), IndexTaskStatus.FAILED, exception.getMessage(), finishedAt, finishedAt);
            log.error("处理索引任务异常: taskId={}", task.id(), exception);
        }
    }

    private IndexTaskStatus resolveFinalStatus(IndexPipeline.IndexPipelineResult result) {
        if (result.skipped()) {
            log.info("索引任务状态分支: pipeline 返回 skipped");
            return IndexTaskStatus.SKIPPED;
        } else if (result.success()) {
            log.info("索引任务状态分支: pipeline 返回 success");
            return IndexTaskStatus.SUCCESS;
        } else {
            log.warn("索引任务状态分支: pipeline 返回 failed");
            return IndexTaskStatus.FAILED;
        }
    }
}
