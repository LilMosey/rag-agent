package com.example.kb.infrastructure.vector;

import com.example.kb.application.port.VectorIndexCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopVectorIndexCleaner implements VectorIndexCleaner {

    private static final Logger log = LoggerFactory.getLogger(NoopVectorIndexCleaner.class);

    @Override
    public void deleteByFileId(Long fileId) {
        log.info("删除向量索引入参: fileId={}", fileId);
        log.info("删除向量索引分支: 第一版不写入 Milvus，保持空实现, fileId={}", fileId);
        log.info("删除向量索引出参: fileId={}, deleted=true", fileId);
    }
}
