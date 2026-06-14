package com.example.kb.application.service;

import com.example.kb.application.port.KnowledgeFileRepository;
import com.example.kb.application.port.ObjectStorage;
import com.example.kb.application.port.VectorIndexCleaner;
import com.example.kb.domain.model.FileStatus;
import com.example.kb.domain.model.FileType;
import com.example.kb.domain.model.KnowledgeFile;
import com.example.kb.domain.service.FileTypePolicy;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

public class KnowledgeFileService {

    private final KnowledgeFileRepository fileRepository;
    private final ObjectStorage objectStorage;
    private final VectorIndexCleaner vectorIndexCleaner;
    private final FileTypePolicy fileTypePolicy = new FileTypePolicy();

    public KnowledgeFileService(
            KnowledgeFileRepository fileRepository,
            ObjectStorage objectStorage,
            VectorIndexCleaner vectorIndexCleaner
    ) {
        this.fileRepository = fileRepository;
        this.objectStorage = objectStorage;
        this.vectorIndexCleaner = vectorIndexCleaner;
    }

    public KnowledgeFile upload(Long knowledgeBaseId, String filename, String contentType, InputStream inputStream, long size) {
        if (fileRepository.existsByKnowledgeBaseIdAndFilename(knowledgeBaseId, filename)) {
            throw new IllegalArgumentException("同一知识库下已存在同名文件，请先删除旧文件。");
        }

        FileType fileType = fileTypePolicy.detect(filename);
        ObjectStorage.PutObjectCommand command = new ObjectStorage.PutObjectCommand(
                knowledgeBaseId,
                filename,
                contentType,
                inputStream,
                size
        );
        ObjectStorage.StoredObject storedObject = objectStorage.putObject(command);
        LocalDateTime now = LocalDateTime.now();
        KnowledgeFile file = new KnowledgeFile(
                null,
                knowledgeBaseId,
                filename,
                fileTypePolicy.extensionOf(filename),
                contentType,
                size,
                storedObject.checksumSha256(),
                storedObject.bucket(),
                storedObject.objectKey(),
                fileType,
                FileStatus.UPLOADED,
                null,
                now,
                now
        );
        return fileRepository.save(file);
    }

    public List<KnowledgeFile> search(Long knowledgeBaseId, String keyword, String status, int page, int size) {
        return fileRepository.search(knowledgeBaseId, keyword, status, page, size);
    }

    public KnowledgeFile get(Long knowledgeBaseId, Long fileId) {
        return fileRepository.findByKnowledgeBaseIdAndFileId(knowledgeBaseId, fileId)
                .orElseThrow(() -> new IllegalArgumentException("文件不存在。"));
    }

    public InputStream download(Long knowledgeBaseId, Long fileId) {
        KnowledgeFile file = get(knowledgeBaseId, fileId);
        return objectStorage.getObject(file.storageBucket(), file.storageObjectKey());
    }

    public void delete(Long knowledgeBaseId, Long fileId) {
        KnowledgeFile file = get(knowledgeBaseId, fileId);
        vectorIndexCleaner.deleteByFileId(fileId);
        objectStorage.deleteObject(file.storageBucket(), file.storageObjectKey());
        fileRepository.deleteByKnowledgeBaseIdAndFileId(knowledgeBaseId, fileId);
    }
}
