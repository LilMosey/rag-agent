package com.example.kb.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.kb.application.port.KnowledgeFileIndexTaskRepository;
import com.example.kb.domain.model.IndexTaskStatus;
import com.example.kb.domain.model.KnowledgeFileIndexTask;
import com.example.kb.infrastructure.persistence.entity.KnowledgeFileIndexTaskEntity;
import com.example.kb.infrastructure.persistence.mapper.KnowledgeFileIndexTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MybatisKnowledgeFileIndexTaskRepository implements KnowledgeFileIndexTaskRepository {

    private static final Logger log = LoggerFactory.getLogger(MybatisKnowledgeFileIndexTaskRepository.class);

    private final KnowledgeFileIndexTaskMapper taskMapper;

    public MybatisKnowledgeFileIndexTaskRepository(KnowledgeFileIndexTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public KnowledgeFileIndexTask save(KnowledgeFileIndexTask task) {
        log.info("保存索引任务入参: id={}, knowledgeBaseId={}, fileId={}, status={}",
                task.id(), task.knowledgeBaseId(), task.fileId(), task.status());
        KnowledgeFileIndexTaskEntity entity = toEntity(task);
        if (entity.getId() == null) {
            log.info("保存索引任务分支: 新增任务, knowledgeBaseId={}, fileId={}",
                    entity.getKnowledgeBaseId(), entity.getFileId());
            taskMapper.insert(entity);
        } else {
            log.info("保存索引任务分支: 更新任务, id={}", entity.getId());
            taskMapper.updateById(entity);
        }
        KnowledgeFileIndexTask saved = toDomain(entity);
        log.info("保存索引任务出参: id={}, fileId={}, status={}, retryCount={}",
                saved.id(), saved.fileId(), saved.status(), saved.retryCount());
        return saved;
    }

    @Override
    public List<KnowledgeFileIndexTask> findPending(int limit) {
        log.info("查询待处理索引任务入参: limit={}", limit);
        if (limit <= 0) {
            log.warn("查询待处理索引任务分支: limit 非法或为零, limit={}", limit);
            return List.of();
        }
        log.info("查询待处理索引任务分支: 查询 PENDING 任务, limit={}", limit);
        LambdaQueryWrapper<KnowledgeFileIndexTaskEntity> wrapper = new LambdaQueryWrapper<KnowledgeFileIndexTaskEntity>()
                .eq(KnowledgeFileIndexTaskEntity::getStatus, IndexTaskStatus.PENDING.name())
                .orderByAsc(KnowledgeFileIndexTaskEntity::getCreatedAt);
        Page<KnowledgeFileIndexTaskEntity> pageRequest = Page.of(1, limit, false);
        List<KnowledgeFileIndexTask> tasks = taskMapper.selectPage(pageRequest, wrapper).getRecords().stream()
                .map(this::toDomain)
                .toList();
        log.info("查询待处理索引任务出参: count={}", tasks.size());
        return tasks;
    }

    @Override
    public boolean markRunning(Long taskId, LocalDateTime startedAt, LocalDateTime updatedAt) {
        log.info("标记索引任务运行中入参: taskId={}, startedAt={}, updatedAt={}", taskId, startedAt, updatedAt);
        LambdaUpdateWrapper<KnowledgeFileIndexTaskEntity> wrapper = new LambdaUpdateWrapper<KnowledgeFileIndexTaskEntity>()
                .eq(KnowledgeFileIndexTaskEntity::getId, taskId)
                .eq(KnowledgeFileIndexTaskEntity::getStatus, IndexTaskStatus.PENDING.name())
                .set(KnowledgeFileIndexTaskEntity::getStatus, IndexTaskStatus.RUNNING.name())
                .set(KnowledgeFileIndexTaskEntity::getStartedAt, startedAt)
                .set(KnowledgeFileIndexTaskEntity::getUpdatedAt, updatedAt);
        int updatedRows = taskMapper.update(null, wrapper);
        boolean marked = updatedRows > 0;
        if (marked) {
            log.info("标记索引任务运行中分支: 更新成功, taskId={}, updatedRows={}", taskId, updatedRows);
        } else {
            log.warn("标记索引任务运行中分支: 未更新, 任务可能不存在或状态不是 PENDING, taskId={}", taskId);
        }
        log.info("标记索引任务运行中出参: taskId={}, marked={}", taskId, marked);
        return marked;
    }

    @Override
    public void markFinished(
            Long taskId,
            IndexTaskStatus status,
            String errorMessage,
            LocalDateTime finishedAt,
            LocalDateTime updatedAt
    ) {
        log.info("标记索引任务完成入参: taskId={}, status={}, hasErrorMessage={}, finishedAt={}, updatedAt={}",
                taskId, status, errorMessage != null && !errorMessage.isBlank(), finishedAt, updatedAt);
        if (errorMessage == null || errorMessage.isBlank()) {
            log.info("标记索引任务完成分支: 无错误信息, taskId={}, status={}", taskId, status);
        } else {
            log.warn("标记索引任务完成分支: 带错误信息, taskId={}, status={}", taskId, status);
        }
        LambdaUpdateWrapper<KnowledgeFileIndexTaskEntity> wrapper = new LambdaUpdateWrapper<KnowledgeFileIndexTaskEntity>()
                .eq(KnowledgeFileIndexTaskEntity::getId, taskId)
                .set(KnowledgeFileIndexTaskEntity::getStatus, status.name())
                .set(KnowledgeFileIndexTaskEntity::getErrorMessage, errorMessage)
                .set(KnowledgeFileIndexTaskEntity::getFinishedAt, finishedAt)
                .set(KnowledgeFileIndexTaskEntity::getUpdatedAt, updatedAt);
        int updatedRows = taskMapper.update(null, wrapper);
        if (updatedRows > 0) {
            log.info("标记索引任务完成分支: 更新成功, taskId={}, updatedRows={}", taskId, updatedRows);
        } else {
            log.warn("标记索引任务完成分支: 未更新, 任务可能不存在, taskId={}", taskId);
        }
        log.info("标记索引任务完成出参: taskId={}, updatedRows={}", taskId, updatedRows);
    }

    private KnowledgeFileIndexTask toDomain(KnowledgeFileIndexTaskEntity entity) {
        return new KnowledgeFileIndexTask(
                entity.getId(),
                entity.getKnowledgeBaseId(),
                entity.getFileId(),
                IndexTaskStatus.valueOf(entity.getStatus()),
                entity.getRetryCount(),
                entity.getMaxRetry(),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private KnowledgeFileIndexTaskEntity toEntity(KnowledgeFileIndexTask task) {
        KnowledgeFileIndexTaskEntity entity = new KnowledgeFileIndexTaskEntity();
        entity.setId(task.id());
        entity.setKnowledgeBaseId(task.knowledgeBaseId());
        entity.setFileId(task.fileId());
        entity.setStatus(task.status().name());
        entity.setRetryCount(task.retryCount());
        entity.setMaxRetry(task.maxRetry());
        entity.setErrorMessage(task.errorMessage());
        entity.setStartedAt(task.startedAt());
        entity.setFinishedAt(task.finishedAt());
        entity.setCreatedAt(task.createdAt());
        entity.setUpdatedAt(task.updatedAt());
        return entity;
    }
}
