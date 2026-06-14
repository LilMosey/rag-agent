package com.example.kb.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.kb.application.port.KnowledgeFileRepository;
import com.example.kb.domain.model.FileStatus;
import com.example.kb.domain.model.FileType;
import com.example.kb.domain.model.KnowledgeFile;
import com.example.kb.infrastructure.persistence.entity.KnowledgeFileEntity;
import com.example.kb.infrastructure.persistence.mapper.KnowledgeFileMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MybatisKnowledgeFileRepository implements KnowledgeFileRepository {

    private final KnowledgeFileMapper knowledgeFileMapper;

    public MybatisKnowledgeFileRepository(KnowledgeFileMapper knowledgeFileMapper) {
        this.knowledgeFileMapper = knowledgeFileMapper;
    }

    @Override
    public boolean existsByKnowledgeBaseIdAndFilename(Long knowledgeBaseId, String filename) {
        LambdaQueryWrapper<KnowledgeFileEntity> wrapper = new LambdaQueryWrapper<KnowledgeFileEntity>()
                .eq(KnowledgeFileEntity::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeFileEntity::getOriginalFilename, filename);
        return knowledgeFileMapper.selectCount(wrapper) > 0;
    }

    @Override
    public KnowledgeFile save(KnowledgeFile file) {
        KnowledgeFileEntity entity = toEntity(file);
        if (entity.getId() == null) {
            knowledgeFileMapper.insert(entity);
        } else {
            knowledgeFileMapper.updateById(entity);
        }
        return toDomain(entity);
    }

    @Override
    public List<KnowledgeFile> search(Long knowledgeBaseId, String keyword, String status, int page, int size) {
        LambdaQueryWrapper<KnowledgeFileEntity> wrapper = new LambdaQueryWrapper<KnowledgeFileEntity>()
                .eq(KnowledgeFileEntity::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeFileEntity::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(KnowledgeFileEntity::getOriginalFilename, keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(KnowledgeFileEntity::getFileStatus, status);
        }
        Page<KnowledgeFileEntity> pageRequest = Page.of(page, size);
        return knowledgeFileMapper.selectPage(pageRequest, wrapper).getRecords().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<KnowledgeFile> findByKnowledgeBaseIdAndFileId(Long knowledgeBaseId, Long fileId) {
        LambdaQueryWrapper<KnowledgeFileEntity> wrapper = new LambdaQueryWrapper<KnowledgeFileEntity>()
                .eq(KnowledgeFileEntity::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeFileEntity::getId, fileId);
        KnowledgeFileEntity entity = knowledgeFileMapper.selectOne(wrapper);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public void deleteByKnowledgeBaseIdAndFileId(Long knowledgeBaseId, Long fileId) {
        LambdaQueryWrapper<KnowledgeFileEntity> wrapper = new LambdaQueryWrapper<KnowledgeFileEntity>()
                .eq(KnowledgeFileEntity::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeFileEntity::getId, fileId);
        knowledgeFileMapper.delete(wrapper);
    }

    private KnowledgeFile toDomain(KnowledgeFileEntity entity) {
        return new KnowledgeFile(
                entity.getId(),
                entity.getKnowledgeBaseId(),
                entity.getOriginalFilename(),
                entity.getFileExt(),
                entity.getContentType(),
                entity.getFileSize(),
                entity.getChecksumSha256(),
                entity.getStorageBucket(),
                entity.getStorageObjectKey(),
                FileType.valueOf(entity.getFileType()),
                FileStatus.valueOf(entity.getFileStatus()),
                entity.getParseError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private KnowledgeFileEntity toEntity(KnowledgeFile file) {
        KnowledgeFileEntity entity = new KnowledgeFileEntity();
        entity.setId(file.id());
        entity.setKnowledgeBaseId(file.knowledgeBaseId());
        entity.setOriginalFilename(file.originalFilename());
        entity.setFileExt(file.fileExt());
        entity.setContentType(file.contentType());
        entity.setFileSize(file.fileSize());
        entity.setChecksumSha256(file.checksumSha256());
        entity.setStorageBucket(file.storageBucket());
        entity.setStorageObjectKey(file.storageObjectKey());
        entity.setFileType(file.fileType().name());
        entity.setFileStatus(file.fileStatus().name());
        entity.setParseError(file.parseError());
        entity.setCreatedAt(file.createdAt());
        entity.setUpdatedAt(file.updatedAt());
        return entity;
    }
}
