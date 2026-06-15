package com.example.kb.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.kb.application.port.KnowledgeBaseRepository;
import com.example.kb.domain.model.KnowledgeBase;
import com.example.kb.infrastructure.persistence.entity.KnowledgeBaseEntity;
import com.example.kb.infrastructure.persistence.entity.KnowledgeFileEntity;
import com.example.kb.infrastructure.persistence.mapper.KnowledgeBaseMapper;
import com.example.kb.infrastructure.persistence.mapper.KnowledgeFileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MybatisKnowledgeBaseRepository implements KnowledgeBaseRepository {

    private static final Logger log = LoggerFactory.getLogger(MybatisKnowledgeBaseRepository.class);

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
        log.info("保存知识库入参: id={}, name={}", knowledgeBase.id(), knowledgeBase.name());
        KnowledgeBaseEntity entity = toEntity(knowledgeBase);
        if (entity.getId() == null) {
            log.info("保存知识库分支: 新增知识库, name={}", entity.getName());
            knowledgeBaseMapper.insert(entity);
        } else {
            log.info("保存知识库分支: 更新知识库, id={}", entity.getId());
            knowledgeBaseMapper.updateById(entity);
        }
        KnowledgeBase saved = toDomain(entity);
        log.info("保存知识库出参: id={}, name={}", saved.id(), saved.name());
        return saved;
    }

    @Override
    public List<KnowledgeBase> findAll() {
        log.info("查询全部知识库入参: none");
        LambdaQueryWrapper<KnowledgeBaseEntity> wrapper = new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .orderByDesc(KnowledgeBaseEntity::getCreatedAt);
        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .toList();
        log.info("查询全部知识库出参: count={}", knowledgeBases.size());
        return knowledgeBases;
    }

    @Override
    public Optional<KnowledgeBase> findById(Long id) {
        log.info("按 ID 查询知识库入参: id={}", id);
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(id);
        Optional<KnowledgeBase> result = Optional.ofNullable(entity).map(this::toDomain);
        if (result.isPresent()) {
            log.info("按 ID 查询知识库分支: 命中, id={}", id);
        } else {
            log.warn("按 ID 查询知识库分支: 未命中, id={}", id);
        }
        return result;
    }

    @Override
    public boolean existsFiles(Long knowledgeBaseId) {
        log.info("检查知识库文件存在入参: knowledgeBaseId={}", knowledgeBaseId);
        LambdaQueryWrapper<KnowledgeFileEntity> wrapper = new LambdaQueryWrapper<KnowledgeFileEntity>()
                .eq(KnowledgeFileEntity::getKnowledgeBaseId, knowledgeBaseId);
        boolean exists = knowledgeFileMapper.selectCount(wrapper) > 0;
        log.info("检查知识库文件存在出参: knowledgeBaseId={}, exists={}", knowledgeBaseId, exists);
        return exists;
    }

    @Override
    public void deleteById(Long id) {
        log.info("删除知识库元数据入参: id={}", id);
        knowledgeBaseMapper.deleteById(id);
        log.info("删除知识库元数据出参: id={}, deleted=true", id);
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
