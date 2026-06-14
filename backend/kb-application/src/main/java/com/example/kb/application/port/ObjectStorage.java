package com.example.kb.application.port;

import java.io.InputStream;

public interface ObjectStorage {

    StoredObject putObject(PutObjectCommand command);

    InputStream getObject(String bucket, String objectKey);

    void deleteObject(String bucket, String objectKey);

    record PutObjectCommand(
            Long knowledgeBaseId,
            String originalFilename,
            String contentType,
            InputStream inputStream,
            long size
    ) {
    }

    record StoredObject(
            String bucket,
            String objectKey,
            String checksumSha256
    ) {
    }
}
