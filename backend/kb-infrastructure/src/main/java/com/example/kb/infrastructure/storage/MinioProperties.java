package com.example.kb.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.minio")
public record MinioProperties(
        String endpoint,
        String bucket,
        String accessKey,
        String secretKey
) {
}
