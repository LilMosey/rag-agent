package com.example.kb.infrastructure.storage;

import com.example.kb.application.port.ObjectStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class MinioObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorage.class);

    private final MinioProperties minioProperties;
    private final MinioClient minioClient;

    public MinioObjectStorage(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
        log.info("初始化 MinIO 客户端入参: endpoint={}, bucket={}, accessKey={}", minioProperties.endpoint(), minioProperties.bucket(), minioProperties.accessKey());
        this.minioClient = MinioClient.builder()
                .endpoint(minioProperties.endpoint())
                .credentials(minioProperties.accessKey(), minioProperties.secretKey())
                .build();
        log.info("初始化 MinIO 客户端出参: initialized=true");
    }

    @Override
    public StoredObject putObject(PutObjectCommand command) {
        log.info("MinIO 上传入参: knowledgeBaseId={}, filename={}, contentType={}, size={}, bucket={}",
                command.knowledgeBaseId(), command.originalFilename(), command.contentType(), command.size(), minioProperties.bucket());
        try {
            byte[] bytes = readAll(command.inputStream());
            log.info("MinIO 上传分支: 文件流读取完成, filename={}, bytes={}", command.originalFilename(), bytes.length);
            ensureBucketExists();
            String checksumSha256 = sha256(bytes);
            String objectKey = buildObjectKey(command.knowledgeBaseId(), command.originalFilename());
            ByteArrayInputStream uploadStream = new ByteArrayInputStream(bytes);
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(minioProperties.bucket())
                    .object(objectKey)
                    .contentType(resolveContentType(command.contentType()))
                    .stream(uploadStream, bytes.length, -1)
                    .build();
            minioClient.putObject(putObjectArgs);
            log.info("MinIO 上传出参: bucket={}, objectKey={}, checksumSha256={}", minioProperties.bucket(), objectKey, checksumSha256);
            return new StoredObject(minioProperties.bucket(), objectKey, checksumSha256);
        } catch (Exception exception) {
            log.error("MinIO 上传异常: endpoint={}, bucket={}, filename={}, size={}",
                    minioProperties.endpoint(), minioProperties.bucket(), command.originalFilename(), command.size(), exception);
            throw new IllegalStateException("文件上传到 MinIO 失败。", exception);
        }
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        log.info("MinIO 下载入参: bucket={}, objectKey={}", bucket, objectKey);
        try {
            GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build();
            InputStream inputStream = minioClient.getObject(getObjectArgs);
            log.info("MinIO 下载出参: bucket={}, objectKey={}, streamCreated=true", bucket, objectKey);
            return inputStream;
        } catch (Exception exception) {
            log.error("MinIO 下载异常: bucket={}, objectKey={}", bucket, objectKey, exception);
            throw new IllegalStateException("从 MinIO 下载文件失败。", exception);
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        log.info("MinIO 删除入参: bucket={}, objectKey={}", bucket, objectKey);
        try {
            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build();
            minioClient.removeObject(removeObjectArgs);
            log.info("MinIO 删除出参: bucket={}, objectKey={}, deleted=true", bucket, objectKey);
        } catch (Exception exception) {
            log.error("MinIO 删除异常: bucket={}, objectKey={}", bucket, objectKey, exception);
            throw new IllegalStateException("删除 MinIO 文件失败。", exception);
        }
    }

    private String buildObjectKey(Long knowledgeBaseId, String originalFilename) {
        log.info("MinIO objectKey 构建入参: knowledgeBaseId={}, originalFilename={}", knowledgeBaseId, originalFilename);
        String safeFilename = originalFilename.replace("\\", "_").replace("/", "_");
        String objectKey = "knowledge-bases/%d/files/%s/%s".formatted(
                knowledgeBaseId,
                UUID.randomUUID(),
                safeFilename
        );
        log.info("MinIO objectKey 构建出参: objectKey={}", objectKey);
        return objectKey;
    }

    private byte[] readAll(InputStream inputStream) throws Exception {
        log.info("读取上传文件流入参: inputStream={}", inputStream.getClass().getName());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        inputStream.transferTo(outputStream);
        byte[] bytes = outputStream.toByteArray();
        log.info("读取上传文件流出参: bytes={}", bytes.length);
        return bytes;
    }

    private String sha256(byte[] bytes) throws Exception {
        log.info("计算 SHA256 入参: bytes={}", bytes.length);
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        byte[] digest = messageDigest.digest(bytes);
        String checksumSha256 = HexFormat.of().formatHex(digest);
        log.info("计算 SHA256 出参: checksumSha256={}", checksumSha256);
        return checksumSha256;
    }

    private void ensureBucketExists() throws Exception {
        log.info("检查 MinIO bucket 入参: bucket={}", minioProperties.bucket());
        BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder()
                .bucket(minioProperties.bucket())
                .build();
        boolean exists = minioClient.bucketExists(bucketExistsArgs);
        if (exists) {
            log.info("检查 MinIO bucket 分支: bucket 已存在, bucket={}", minioProperties.bucket());
        } else {
            log.warn("检查 MinIO bucket 分支: bucket 不存在, 准备创建, bucket={}", minioProperties.bucket());
            MakeBucketArgs makeBucketArgs = MakeBucketArgs.builder()
                    .bucket(minioProperties.bucket())
                    .build();
            minioClient.makeBucket(makeBucketArgs);
            log.info("创建 MinIO bucket 出参: bucket={}, created=true", minioProperties.bucket());
        }
    }

    private String resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            log.info("解析 contentType 分支: contentType 为空, 使用 application/octet-stream");
            return "application/octet-stream";
        } else {
            log.info("解析 contentType 分支: 使用上传 contentType={}", contentType);
            return contentType;
        }
    }
}
