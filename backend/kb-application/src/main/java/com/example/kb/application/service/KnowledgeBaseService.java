package com.example.kb.application.service;

import com.example.kb.application.port.KnowledgeBaseRepository;
import com.example.kb.domain.model.KnowledgeBase;

import java.time.LocalDateTime;
import java.util.List;

public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    public KnowledgeBase create(String name, String description) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase knowledgeBase = new KnowledgeBase(null, name, description, now, now);
        return knowledgeBaseRepository.save(knowledgeBase);
    }

    public List<KnowledgeBase> list() {
        return knowledgeBaseRepository.findAll();
    }

    public KnowledgeBase get(Long id) {
        return knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在。"));
    }

    public KnowledgeBase update(Long id, String name, String description) {
        KnowledgeBase existing = get(id);
        KnowledgeBase updated = new KnowledgeBase(
                existing.id(),
                name,
                description,
                existing.createdAt(),
                LocalDateTime.now()
        );
        return knowledgeBaseRepository.save(updated);
    }

    public void delete(Long id) {
        if (knowledgeBaseRepository.existsFiles(id)) {
            throw new IllegalArgumentException("知识库下仍有文件，请先删除文件。");
        }
        knowledgeBaseRepository.deleteById(id);
    }
}
