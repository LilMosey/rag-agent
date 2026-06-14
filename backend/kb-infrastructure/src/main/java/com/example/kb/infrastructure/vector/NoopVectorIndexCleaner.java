package com.example.kb.infrastructure.vector;

import com.example.kb.application.port.VectorIndexCleaner;
import org.springframework.stereotype.Component;

@Component
public class NoopVectorIndexCleaner implements VectorIndexCleaner {

    @Override
    public void deleteByFileId(Long fileId) {
        // 第一版不写入 Milvus，删除向量数据时保持空实现。
    }
}
