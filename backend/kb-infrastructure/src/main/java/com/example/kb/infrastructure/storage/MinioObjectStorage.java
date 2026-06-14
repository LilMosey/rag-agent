package com.example.kb.infrastructure.storage;

import com.example.kb.application.port.ObjectStorage;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class MinioObjectStorage implements ObjectStorage {

    private final MinioProperties minioProperties;
    private final MinioClient minioClient;

    public MinioObjectStorage(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
        this.minioClient = MinioClient.builder()
                .endpoint(minioProperties.endpoint())
                .credentials(minioProperties.accessKey(), minioProperties.secretKey())
                .build();
    }

    @Override
    public StoredObject putObject(PutObjectCommand command) {
        try {
            byte[] bytes = readAll(command.inputStream());
            String checksumSha256 = sha256(bytes);
            String objectKey = buildObjectKey(command.knowledgeBaseId(), command.originalFilename());
            ByteArrayInputStream uploadStream = new ByteArrayInputStream(bytes);
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(minioProperties.bucket())
                    .object(objectKey)
                    .contentType(command.contentType())
                    .stream(uploadStream, command.size(), -1)
                    .build();
            minioClient.putObject(putObjectArgs);
            return new StoredObject(minioProperties.bucket(), objectKey, checksumSha256);
        } catch (Exception exception) {
            throw new IllegalStateException("文件上传到 MinIO 失败。", exception);
        }
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        try {
            GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build();
            return minioClient.getObject(getObjectArgs);
        } catch (Exception exception) {
            throw new IllegalStateException("从 MinIO 下载文件失败。", exception);
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        try {
            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build();
            minioClient.removeObject(removeObjectArgs);
        } catch (Exception exception) {
            throw new IllegalStateException("删除 MinIO 文件失败。", exception);
        }
    }

    private String buildObjectKey(Long knowledgeBaseId, String originalFilename) {
        String safeFilename = originalFilename.replace("\\", "_").replace("/", "_");
        return "knowledge-bases/%d/files/%s/%s".formatted(
                knowledgeBaseId,
                UUID.randomUUID(),
                safeFilename
        );
    }

    private byte[] readAll(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        inputStream.transferTo(outputStream);
        return outputStream.toByteArray();
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        byte[] digest = messageDigest.digest(bytes);
        return HexFormat.of().formatHex(digest);
    }
}
