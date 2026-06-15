package com.example.kb.application.service;

import com.example.kb.application.port.KnowledgeBaseRepository;
import com.example.kb.domain.model.KnowledgeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    public KnowledgeBase create(String name, String description) {
        log.info("创建知识库入参: name={}, description={}", name, description);
        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase knowledgeBase = new KnowledgeBase(null, name, description, now, now);
        KnowledgeBase saved = knowledgeBaseRepository.save(knowledgeBase);
        log.info("创建知识库出参: id={}, name={}", saved.id(), saved.name());
        return saved;
    }

    public List<KnowledgeBase> list() {
        log.info("查询知识库列表入参: none");
        List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findAll();
        log.info("查询知识库列表出参: count={}", knowledgeBases.size());
        return knowledgeBases;
    }

    public KnowledgeBase get(Long id) {
        log.info("查询知识库详情入参: id={}", id);
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在。"));
        log.info("查询知识库详情出参: id={}, name={}", knowledgeBase.id(), knowledgeBase.name());
        return knowledgeBase;
    }

    public KnowledgeBase update(Long id, String name, String description) {
        log.info("更新知识库入参: id={}, name={}, description={}", id, name, description);
        KnowledgeBase existing = get(id);
        KnowledgeBase updated = new KnowledgeBase(
                existing.id(),
                name,
                description,
                existing.createdAt(),
                LocalDateTime.now()
        );
        KnowledgeBase saved = knowledgeBaseRepository.save(updated);
        log.info("更新知识库出参: id={}, name={}", saved.id(), saved.name());
        return saved;
    }

    public void delete(Long id) {
        log.info("删除知识库入参: id={}", id);
        if (knowledgeBaseRepository.existsFiles(id)) {
            log.warn("删除知识库分支: 知识库下仍有文件, id={}", id);
            throw new IllegalArgumentException("知识库下仍有文件，请先删除文件。");
        } else {
            log.info("删除知识库分支: 知识库为空, id={}", id);
        }
        knowledgeBaseRepository.deleteById(id);
        log.info("删除知识库出参: id={}, deleted=true", id);
    }
}
