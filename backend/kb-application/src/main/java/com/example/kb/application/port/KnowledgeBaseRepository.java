package com.example.kb.application.port;

import com.example.kb.domain.model.KnowledgeBase;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository {

    KnowledgeBase save(KnowledgeBase knowledgeBase);

    List<KnowledgeBase> findAll();

    Optional<KnowledgeBase> findById(Long id);

    boolean existsFiles(Long knowledgeBaseId);

    void deleteById(Long id);
}
