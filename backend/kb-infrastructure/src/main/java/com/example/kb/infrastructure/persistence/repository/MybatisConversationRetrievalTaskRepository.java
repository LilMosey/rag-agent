package com.example.kb.infrastructure.persistence.repository;

import com.example.kb.application.port.ConversationRetrievalTaskRepository;
import com.example.kb.domain.model.ConversationRetrievalTask;
import com.example.kb.domain.model.RetrievalTaskStatus;
import com.example.kb.domain.model.RetrievalTaskType;
import com.example.kb.infrastructure.persistence.entity.ConversationRetrievalTaskEntity;
import com.example.kb.infrastructure.persistence.mapper.ConversationRetrievalTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MybatisConversationRetrievalTaskRepository implements ConversationRetrievalTaskRepository {

    private static final Logger log = LoggerFactory.getLogger(MybatisConversationRetrievalTaskRepository.class);
    private static final int BATCH_INSERT_SIZE = 100;

    private final ConversationRetrievalTaskMapper conversationRetrievalTaskMapper;

    public MybatisConversationRetrievalTaskRepository(ConversationRetrievalTaskMapper conversationRetrievalTaskMapper) {
        this.conversationRetrievalTaskMapper = conversationRetrievalTaskMapper;
    }

    @Override
    public ConversationRetrievalTask save(ConversationRetrievalTask task) {
        log.info("保存检索任务入参: retrievalId={}, taskType={}, status={}",
                task.conversationRetrievalId(), task.taskType(), task.status());
        ConversationRetrievalTaskEntity entity = toEntity(task);
        fillTime(entity);
        conversationRetrievalTaskMapper.insert(entity);
        ConversationRetrievalTask saved = toDomain(entity);
        log.info("保存检索任务出参: id={}, retrievalId={}, taskType={}",
                saved.id(), saved.conversationRetrievalId(), saved.taskType());
        return saved;
    }

    @Override
    public void saveBatch(List<ConversationRetrievalTask> tasks) {
        log.info("批量保存检索任务入参: count={}", tasks.size());
        if (tasks.isEmpty()) {
            log.info("批量保存检索任务分支: 空列表");
            return;
        }
        List<ConversationRetrievalTaskEntity> entities = new ArrayList<>(tasks.size());
        for (ConversationRetrievalTask task : tasks) {
            ConversationRetrievalTaskEntity entity = toEntity(task);
            fillTime(entity);
            entities.add(entity);
        }
        int insertedRows = 0;
        int batchCount = 0;
        for (int fromIndex = 0; fromIndex < entities.size(); fromIndex += BATCH_INSERT_SIZE) {
            int toIndex = Math.min(fromIndex + BATCH_INSERT_SIZE, entities.size());
            List<ConversationRetrievalTaskEntity> batchEntities = entities.subList(fromIndex, toIndex);
            insertedRows += conversationRetrievalTaskMapper.insertBatch(batchEntities);
            batchCount++;
        }
        log.info("批量保存检索任务出参: insertedRows={}, batchSize={}, batchCount={}",
                insertedRows, BATCH_INSERT_SIZE, batchCount);
    }

    private void fillTime(ConversationRetrievalTaskEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
    }

    private ConversationRetrievalTask toDomain(ConversationRetrievalTaskEntity entity) {
        return new ConversationRetrievalTask(
                entity.getId(),
                entity.getConversationRetrievalId(),
                RetrievalTaskType.valueOf(entity.getTaskType()),
                entity.getQueryText(),
                RetrievalTaskStatus.valueOf(entity.getStatus()),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ConversationRetrievalTaskEntity toEntity(ConversationRetrievalTask task) {
        ConversationRetrievalTaskEntity entity = new ConversationRetrievalTaskEntity();
        entity.setId(task.id());
        entity.setConversationRetrievalId(task.conversationRetrievalId());
        entity.setTaskType(task.taskType().name());
        entity.setQueryText(task.queryText());
        entity.setStatus(task.status().name());
        entity.setErrorMessage(task.errorMessage());
        entity.setStartedAt(task.startedAt());
        entity.setFinishedAt(task.finishedAt());
        entity.setCreatedAt(task.createdAt());
        entity.setUpdatedAt(task.updatedAt());
        return entity;
    }
}
