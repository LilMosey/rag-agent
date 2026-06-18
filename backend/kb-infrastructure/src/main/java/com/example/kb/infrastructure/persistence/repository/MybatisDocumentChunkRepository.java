package com.example.kb.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.kb.application.port.DocumentChunkRepository;
import com.example.kb.domain.model.ChunkStrategy;
import com.example.kb.domain.model.DocumentChunk;
import com.example.kb.infrastructure.persistence.entity.DocumentChunkEntity;
import com.example.kb.infrastructure.persistence.mapper.DocumentChunkMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MybatisDocumentChunkRepository implements DocumentChunkRepository {

    private static final Logger log = LoggerFactory.getLogger(MybatisDocumentChunkRepository.class);

    private final DocumentChunkMapper documentChunkMapper;

    public MybatisDocumentChunkRepository(DocumentChunkMapper documentChunkMapper) {
        this.documentChunkMapper = documentChunkMapper;
    }

    @Override
    public void deleteByFileId(Long fileId) {
        log.info("删除 chunk 元数据入参: fileId={}", fileId);
        LambdaQueryWrapper<DocumentChunkEntity> wrapper = new LambdaQueryWrapper<DocumentChunkEntity>()
                .eq(DocumentChunkEntity::getFileId, fileId);
        int deletedRows = documentChunkMapper.delete(wrapper);
        log.info("删除 chunk 元数据出参: fileId={}, deletedRows={}", fileId, deletedRows);
    }

    @Override
    public DocumentChunk save(DocumentChunk chunk) {
        log.info("保存 chunk 元数据入参: fileId={}, chunkIndex={}, strategy={}, contentSize={}",
                chunk.fileId(), chunk.chunkIndex(), chunk.chunkStrategy().logName(), chunk.contentSize());
        DocumentChunkEntity entity = toEntity(chunk);
        documentChunkMapper.insert(entity);
        DocumentChunk saved = toDomain(entity);
        log.info("保存 chunk 元数据出参: id={}, fileId={}, chunkIndex={}", saved.id(), saved.fileId(), saved.chunkIndex());
        return saved;
    }

    @Override
    public List<DocumentChunk> findByFileId(Long fileId) {
        log.info("查询 chunk 元数据入参: fileId={}", fileId);
        LambdaQueryWrapper<DocumentChunkEntity> wrapper = new LambdaQueryWrapper<DocumentChunkEntity>()
                .eq(DocumentChunkEntity::getFileId, fileId)
                .orderByAsc(DocumentChunkEntity::getChunkIndex);
        List<DocumentChunk> chunks = documentChunkMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .toList();
        log.info("查询 chunk 元数据出参: fileId={}, count={}", fileId, chunks.size());
        return chunks;
    }

    private DocumentChunk toDomain(DocumentChunkEntity entity) {
        return new DocumentChunk(
                entity.getId(),
                entity.getKnowledgeBaseId(),
                entity.getFileId(),
                entity.getSectionId(),
                entity.getParentSectionId(),
                entity.getChunkIndex(),
                ChunkStrategy.valueOf(entity.getChunkStrategy()),
                entity.getChunkSize(),
                entity.getChunkOverlap(),
                entity.getTitlePath(),
                entity.getContentPreview(),
                entity.getContentHash(),
                entity.getContentSize(),
                entity.getStartOffset(),
                entity.getEndOffset(),
                entity.getStorageBucket(),
                entity.getStorageObjectKey(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DocumentChunkEntity toEntity(DocumentChunk chunk) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.setId(chunk.id());
        entity.setKnowledgeBaseId(chunk.knowledgeBaseId());
        entity.setFileId(chunk.fileId());
        entity.setSectionId(chunk.sectionId());
        entity.setParentSectionId(chunk.parentSectionId());
        entity.setChunkIndex(chunk.chunkIndex());
        entity.setChunkStrategy(chunk.chunkStrategy().name());
        entity.setChunkSize(chunk.chunkSize());
        entity.setChunkOverlap(chunk.chunkOverlap());
        entity.setTitlePath(chunk.titlePath());
        entity.setContentPreview(chunk.contentPreview());
        entity.setContentHash(chunk.contentHash());
        entity.setContentSize(chunk.contentSize());
        entity.setStartOffset(chunk.startOffset());
        entity.setEndOffset(chunk.endOffset());
        entity.setStorageBucket(chunk.storageBucket());
        entity.setStorageObjectKey(chunk.storageObjectKey());
        entity.setCreatedAt(chunk.createdAt());
        entity.setUpdatedAt(chunk.updatedAt());
        return entity;
    }
}
