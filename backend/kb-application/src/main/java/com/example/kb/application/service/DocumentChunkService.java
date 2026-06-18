package com.example.kb.application.service;

import com.example.kb.application.port.ChunkObjectStorage;
import com.example.kb.application.port.DocumentChunkRepository;
import com.example.kb.application.port.DocumentChunker;
import com.example.kb.domain.model.ChunkConfig;
import com.example.kb.domain.model.DocumentChunk;
import com.example.kb.domain.model.KnowledgeFile;
import com.example.kb.domain.model.ParsedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DocumentChunkService {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkService.class);
    private static final int PREVIEW_LENGTH = 200;

    private final DocumentChunker documentChunker;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkObjectStorage chunkObjectStorage;

    public DocumentChunkService(
            DocumentChunker documentChunker,
            DocumentChunkRepository documentChunkRepository,
            ChunkObjectStorage chunkObjectStorage
    ) {
        this.documentChunker = documentChunker;
        this.documentChunkRepository = documentChunkRepository;
        this.chunkObjectStorage = chunkObjectStorage;
    }

    public List<DocumentChunk> rebuildChunks(KnowledgeFile file, ParsedDocument document) {
        log.info("重建 chunk 入参: knowledgeBaseId={}, fileId={}, strategy={}, size={}, overlap={}",
                file.knowledgeBaseId(), file.id(), file.chunkStrategy().logName(), file.chunkSize(), file.chunkOverlap());
        ChunkConfig chunkConfig = ChunkConfig.normalize(
                file.knowledgeBaseId(),
                file.id(),
                file.chunkStrategy(),
                file.chunkSize(),
                file.chunkOverlap()
        );
        chunkObjectStorage.deleteChunksByFile(file.knowledgeBaseId(), file.id());
        documentChunkRepository.deleteByFileId(file.id());
        List<DocumentChunker.ChunkDraft> drafts = documentChunker.chunk(document, chunkConfig);
        List<ChunkObjectStorage.PutChunkCommand> putChunkCommands = new ArrayList<>(drafts.size());
        for (DocumentChunker.ChunkDraft draft : drafts) {
            putChunkCommands.add(new ChunkObjectStorage.PutChunkCommand(
                    file.knowledgeBaseId(),
                    file.id(),
                    draft.chunkIndex(),
                    draft.contentHash(),
                    draft.content()
            ));
        }
        List<ChunkObjectStorage.StoredChunkObject> storedObjects = chunkObjectStorage.putChunks(putChunkCommands);
        if (storedObjects.size() != drafts.size()) {
            log.error("重建 chunk 异常: MinIO 返回数量不一致, draftCount={}, storedCount={}", drafts.size(), storedObjects.size());
            throw new IllegalStateException("chunk 正文批量上传结果数量不一致。");
        }
        List<DocumentChunk> chunks = new ArrayList<>(drafts.size());
        for (int index = 0; index < drafts.size(); index++) {
            DocumentChunker.ChunkDraft draft = drafts.get(index);
            ChunkObjectStorage.StoredChunkObject storedObject = storedObjects.get(index);
            LocalDateTime now = LocalDateTime.now();
            DocumentChunk chunk = new DocumentChunk(
                    null,
                    file.knowledgeBaseId(),
                    file.id(),
                    draft.sectionId(),
                    draft.parentSectionId(),
                    draft.chunkIndex(),
                    chunkConfig.chunkStrategy(),
                    chunkConfig.chunkSize(),
                    chunkConfig.chunkOverlap(),
                    draft.titlePath(),
                    preview(draft.content()),
                    draft.contentHash(),
                    draft.contentSize(),
                    draft.startOffset(),
                    draft.endOffset(),
                    storedObject.bucket(),
                    storedObject.objectKey(),
                    now,
                    now
            );
            chunks.add(chunk);
        }
        List<DocumentChunk> savedChunks = documentChunkRepository.saveBatch(chunks);
        log.info("重建 chunk 出参: knowledgeBaseId={}, fileId={}, strategy={}, chunkCount={}",
                file.knowledgeBaseId(), file.id(), chunkConfig.chunkStrategy().logName(), savedChunks.size());
        return savedChunks;
    }

    private String preview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_LENGTH);
    }
}
