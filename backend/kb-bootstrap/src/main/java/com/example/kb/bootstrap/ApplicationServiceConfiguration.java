package com.example.kb.bootstrap;

import com.example.kb.application.port.IndexPipeline;
import com.example.kb.application.port.KnowledgeBaseRepository;
import com.example.kb.application.port.KnowledgeFileRepository;
import com.example.kb.application.port.KnowledgeFileIndexTaskRepository;
import com.example.kb.application.port.ObjectStorage;
import com.example.kb.application.port.VectorIndexCleaner;
import com.example.kb.application.service.KnowledgeBaseService;
import com.example.kb.application.service.KnowledgeFileIndexTaskService;
import com.example.kb.application.service.KnowledgeFileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationServiceConfiguration {

    @Bean
    public KnowledgeBaseService knowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository) {
        return new KnowledgeBaseService(knowledgeBaseRepository);
    }

    @Bean
    public KnowledgeFileService knowledgeFileService(
            KnowledgeFileRepository knowledgeFileRepository,
            ObjectStorage objectStorage,
            VectorIndexCleaner vectorIndexCleaner,
            KnowledgeFileIndexTaskService knowledgeFileIndexTaskService
    ) {
        return new KnowledgeFileService(
                knowledgeFileRepository,
                objectStorage,
                vectorIndexCleaner,
                knowledgeFileIndexTaskService
        );
    }

    @Bean
    public KnowledgeFileIndexTaskService knowledgeFileIndexTaskService(
            KnowledgeFileIndexTaskRepository knowledgeFileIndexTaskRepository,
            IndexPipeline indexPipeline
    ) {
        return new KnowledgeFileIndexTaskService(knowledgeFileIndexTaskRepository, indexPipeline);
    }
}
