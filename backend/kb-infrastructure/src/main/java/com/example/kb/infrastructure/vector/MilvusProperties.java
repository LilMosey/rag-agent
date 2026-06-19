package com.example.kb.infrastructure.vector;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vector.milvus")
public record MilvusProperties(
        String host,
        int port,
        String database,
        String collectionPrefix
) {

    public String uri() {
        return "http://" + host + ":" + port;
    }

    public String collectionName() {
        return collectionPrefix + "_chunk";
    }
}
