package com.example.kb.infrastructure.persistence.repository;

import com.example.kb.application.port.ConversationRetrievalTaskHitRepository;
import com.example.kb.domain.model.ConversationRetrievalTaskHit;
import com.example.kb.infrastructure.persistence.entity.ConversationRetrievalTaskHitEntity;
import com.example.kb.infrastructure.persistence.mapper.ConversationRetrievalTaskHitMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MybatisConversationRetrievalTaskHitRepository implements ConversationRetrievalTaskHitRepository {

    private static final Logger log = LoggerFactory.getLogger(MybatisConversationRetrievalTaskHitRepository.class);
    private static final int BATCH_INSERT_SIZE = 100;

    private final ConversationRetrievalTaskHitMapper conversationRetrievalTaskHitMapper;

    public MybatisConversationRetrievalTaskHitRepository(ConversationRetrievalTaskHitMapper conversationRetrievalTaskHitMapper) {
        this.conversationRetrievalTaskHitMapper = conversationRetrievalTaskHitMapper;
    }

    @Override
    public void saveBatch(List<ConversationRetrievalTaskHit> hits) {
        log.info("批量保存检索任务命中入参: count={}", hits.size());
        if (hits.isEmpty()) {
            log.info("批量保存检索任务命中分支: 空列表");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<ConversationRetrievalTaskHitEntity> entities = new ArrayList<>(hits.size());
        for (ConversationRetrievalTaskHit hit : hits) {
            ConversationRetrievalTaskHitEntity entity = toEntity(hit);
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(now);
            }
            entities.add(entity);
        }
        int insertedRows = 0;
        int batchCount = 0;
        for (int fromIndex = 0; fromIndex < entities.size(); fromIndex += BATCH_INSERT_SIZE) {
            int toIndex = Math.min(fromIndex + BATCH_INSERT_SIZE, entities.size());
            List<ConversationRetrievalTaskHitEntity> batchEntities = entities.subList(fromIndex, toIndex);
            insertedRows += conversationRetrievalTaskHitMapper.insertBatch(batchEntities);
            batchCount++;
        }
        log.info("批量保存检索任务命中出参: insertedRows={}, batchSize={}, batchCount={}",
                insertedRows, BATCH_INSERT_SIZE, batchCount);
    }

    private ConversationRetrievalTaskHitEntity toEntity(ConversationRetrievalTaskHit hit) {
        ConversationRetrievalTaskHitEntity entity = new ConversationRetrievalTaskHitEntity();
        entity.setId(hit.id());
        entity.setRetrievalTaskId(hit.retrievalTaskId());
        entity.setKnowledgeBaseId(hit.knowledgeBaseId());
        entity.setFileId(hit.fileId());
        entity.setChunkId(hit.chunkId());
        entity.setChunkIndex(hit.chunkIndex());
        entity.setScore(hit.score());
        entity.setRankNo(hit.rankNo());
        entity.setCreatedAt(hit.createdAt());
        return entity;
    }
}
