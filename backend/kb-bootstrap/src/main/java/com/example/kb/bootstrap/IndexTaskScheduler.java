package com.example.kb.bootstrap;

import com.example.kb.application.service.KnowledgeFileIndexTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IndexTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(IndexTaskScheduler.class);

    private final KnowledgeFileIndexTaskService indexTaskService;
    private final int batchSize;

    public IndexTaskScheduler(
            KnowledgeFileIndexTaskService indexTaskService,
            @Value("${app.index.scheduler.batch-size:10}") int batchSize
    ) {
        this.indexTaskService = indexTaskService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.index.scheduler.fixed-delay-ms:5000}")
    public void scanPendingTasks() {
        log.info("索引任务定时扫描入参: batchSize={}", batchSize);
        indexTaskService.processPendingTasks(batchSize);
        log.info("索引任务定时扫描出参: completed=true");
    }
}
