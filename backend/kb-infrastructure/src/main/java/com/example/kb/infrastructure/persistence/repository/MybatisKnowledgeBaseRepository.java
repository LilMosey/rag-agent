package com.example.kb.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.kb.application.port.KnowledgeBaseRepository;
import com.example.kb.domain.model.KnowledgeBase;
import com.example.kb.infrastructure.persistence.entity.KnowledgeBaseEntity;
import com.example.kb.infrastructure.persistence.entity.KnowledgeFileEntity;
import com.example.kb.infrastructure.persistence.mapper.KnowledgeBaseMapper;
import com.example.kb.infrastructure.persistence.mapper.KnowledgeFileMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MybatisKnowledgeBaseRepository implements KnowledgeBaseRepository {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeFileMapper knowledgeFileMapper;

    public MybatisKnowledgeBaseRepository(
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeFileMapper knowledgeFileMapper
    ) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeFileMapper = knowledgeFileMapper;
    }

    @Override
    public KnowledgeBase save(KnowledgeBase knowledgeBase) {
        KnowledgeBaseEntity entity = toEntity(knowledgeBase);
        if (entity.getId() == null) {
            knowledgeBaseMapper.insert(entity);
        } else {
            knowledgeBaseMapper.updateById(entity);
        }
        return toDomain(entity);
    }

    @Override
    public List<KnowledgeBase> findAll() {
        LambdaQueryWrapper<KnowledgeBaseEntity> wrapper = new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .orderByDesc(KnowledgeBaseEntity::getCreatedAt);
        return knowledgeBaseMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<KnowledgeBase> findById(Long id) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(id);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public boolean existsFiles(Long knowledgeBaseId) {
        LambdaQueryWrapper<KnowledgeFileEntity> wrapper = new LambdaQueryWrapper<KnowledgeFileEntity>()
                .eq(KnowledgeFileEntity::getKnowledgeBaseId, knowledgeBaseId);
        return knowledgeFileMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void deleteById(Long id) {
        knowledgeBaseMapper.deleteById(id);
    }

    private KnowledgeBase toDomain(KnowledgeBaseEntity entity) {
        return new KnowledgeBase(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private KnowledgeBaseEntity toEntity(KnowledgeBase knowledgeBase) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(knowledgeBase.id());
        entity.setName(knowledgeBase.name());
        entity.setDescription(knowledgeBase.description());
        entity.setCreatedAt(knowledgeBase.createdAt());
        entity.setUpdatedAt(knowledgeBase.updatedAt());
        return entity;
    }
}
