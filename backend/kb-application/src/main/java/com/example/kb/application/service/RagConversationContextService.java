package com.example.kb.application.service;

import com.example.kb.application.port.ChunkContentStorage;
import com.example.kb.application.port.ConversationMessageRepository;
import com.example.kb.application.port.ConversationRetrievalReferenceRepository;
import com.example.kb.application.port.ConversationRetrievalRepository;
import com.example.kb.application.port.DocumentChunkRepository;
import com.example.kb.application.port.KnowledgeFileRepository;
import com.example.kb.domain.model.ConversationMessage;
import com.example.kb.domain.model.ConversationRetrieval;
import com.example.kb.domain.model.ConversationRetrievalReference;
import com.example.kb.domain.model.DocumentChunk;
import com.example.kb.domain.model.KnowledgeFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RagConversationContextService {

    private static final Logger log = LoggerFactory.getLogger(RagConversationContextService.class);

    private final ConversationMessageRepository conversationMessageRepository;
    private final ConversationRetrievalRepository conversationRetrievalRepository;
    private final ConversationRetrievalReferenceRepository conversationRetrievalReferenceRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final KnowledgeFileRepository knowledgeFileRepository;
    private final ChunkContentStorage chunkContentStorage;
    private final RagContextProperties ragContextProperties;

    public RagConversationContextService(
            ConversationMessageRepository conversationMessageRepository,
            ConversationRetrievalRepository conversationRetrievalRepository,
            ConversationRetrievalReferenceRepository conversationRetrievalReferenceRepository,
            DocumentChunkRepository documentChunkRepository,
            KnowledgeFileRepository knowledgeFileRepository,
            ChunkContentStorage chunkContentStorage,
            RagContextProperties ragContextProperties
    ) {
        this.conversationMessageRepository = conversationMessageRepository;
        this.conversationRetrievalRepository = conversationRetrievalRepository;
        this.conversationRetrievalReferenceRepository = conversationRetrievalReferenceRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.knowledgeFileRepository = knowledgeFileRepository;
        this.chunkContentStorage = chunkContentStorage;
        this.ragContextProperties = ragContextProperties;
    }

    public List<ConversationMessage> recentMessages(Long conversationId, Long excludedMessageId) {
        log.info("查询最近会话上下文入参: conversationId={}, excludedMessageId={}, limit={}",
                conversationId, excludedMessageId, ragContextProperties.safeRecentMessageLimit());
        List<ConversationMessage> messages = conversationMessageRepository.findByConversationId(conversationId);
        List<ConversationMessage> filteredMessages = messages.stream()
                .filter(message -> excludedMessageId == null || !excludedMessageId.equals(message.id()))
                .toList();
        int fromIndex = Math.max(0, filteredMessages.size() - ragContextProperties.safeRecentMessageLimit());
        List<ConversationMessage> recentMessages = filteredMessages.subList(fromIndex, filteredMessages.size());
        log.info("查询最近会话上下文出参: conversationId={}, total={}, selected={}",
                conversationId, filteredMessages.size(), recentMessages.size());
        return recentMessages;
    }

    public Optional<ReusableReferenceContext> latestReusableReferenceContext(Long conversationId) {
        log.info("查询可复用引用上下文入参: conversationId={}, enabled={}",
                conversationId, ragContextProperties.isReuseLastContextEnabled());
        if (!ragContextProperties.isReuseLastContextEnabled()) {
            log.info("查询可复用引用上下文分支: 配置关闭");
            return Optional.empty();
        }
        Optional<ConversationRetrieval> retrievalOptional =
                conversationRetrievalRepository.findLatestWithReferencesByConversationId(conversationId);
        if (retrievalOptional.isEmpty()) {
            log.info("查询可复用引用上下文分支: 未找到上一轮引用, conversationId={}", conversationId);
            return Optional.empty();
        }
        ConversationRetrieval retrieval = retrievalOptional.get();
        List<ConversationRetrievalReference> references =
                conversationRetrievalReferenceRepository.findByRetrievalId(retrieval.id());
        if (references.isEmpty()) {
            log.info("查询可复用引用上下文分支: retrieval 无引用, retrievalId={}", retrieval.id());
            return Optional.empty();
        }
        List<ReusableReference> reusableReferences = hydrateReusableReferences(references);
        if (reusableReferences.isEmpty()) {
            log.warn("查询可复用引用上下文分支: 引用还原后为空, retrievalId={}", retrieval.id());
            return Optional.empty();
        }
        String sourceAnswer = findMessageContent(conversationId, retrieval.messageId());
        log.info("查询可复用引用上下文出参: conversationId={}, retrievalId={}, referenceCount={}",
                conversationId, retrieval.id(), reusableReferences.size());
        return Optional.of(new ReusableReferenceContext(retrieval, retrieval.queryText(), sourceAnswer, reusableReferences));
    }

    private String findMessageContent(Long conversationId, Long messageId) {
        if (messageId == null) {
            log.warn("查询可复用引用回答分支: messageId 为空, conversationId={}", conversationId);
            return "";
        }
        List<ConversationMessage> messages = conversationMessageRepository.findByConversationId(conversationId);
        for (ConversationMessage message : messages) {
            if (messageId.equals(message.id())) {
                log.info("查询可复用引用回答出参: conversationId={}, messageId={}, contentLength={}",
                        conversationId, messageId, message.content().length());
                return message.content();
            }
        }
        log.warn("查询可复用引用回答分支: 未找到回答消息, conversationId={}, messageId={}", conversationId, messageId);
        return "";
    }

    private List<ReusableReference> hydrateReusableReferences(List<ConversationRetrievalReference> references) {
        log.info("还原可复用引用入参: referenceCount={}", references.size());
        List<Long> chunkIds = references.stream()
                .map(ConversationRetrievalReference::chunkId)
                .toList();
        Map<Long, DocumentChunk> chunkMap = new LinkedHashMap<>();
        for (DocumentChunk chunk : documentChunkRepository.findByIds(chunkIds)) {
            chunkMap.put(chunk.id(), chunk);
        }
        List<ReusableReference> reusableReferences = new ArrayList<>();
        int referenceNo = 1;
        int missingChunkCount = 0;
        int missingFileCount = 0;
        for (ConversationRetrievalReference reference : references) {
            DocumentChunk chunk = chunkMap.get(reference.chunkId());
            if (chunk == null) {
                missingChunkCount++;
                log.warn("还原可复用引用分支: chunk 不存在, chunkId={}", reference.chunkId());
                continue;
            }
            Optional<KnowledgeFile> fileOptional = knowledgeFileRepository.findById(chunk.fileId());
            if (fileOptional.isEmpty()) {
                missingFileCount++;
                log.warn("还原可复用引用分支: 文件不存在, fileId={}", chunk.fileId());
                continue;
            }
            String content = chunkContentStorage.getChunkContent(chunk.storageBucket(), chunk.storageObjectKey());
            reusableReferences.add(new ReusableReference(
                    referenceNo,
                    reference.knowledgeBaseId(),
                    reference.fileId(),
                    reference.chunkId(),
                    reference.chunkIndex(),
                    reference.titlePath(),
                    reference.score(),
                    reference.contentPreview(),
                    chunk,
                    fileOptional.get(),
                    content
            ));
            referenceNo++;
        }
        log.info("还原可复用引用出参: inputCount={}, outputCount={}, missingChunkCount={}, missingFileCount={}",
                references.size(), reusableReferences.size(), missingChunkCount, missingFileCount);
        return reusableReferences;
    }

    public record ReusableReferenceContext(
            ConversationRetrieval sourceRetrieval,
            String sourceQuestion,
            String sourceAnswer,
            List<ReusableReference> references
    ) {
    }

    public record ReusableReference(
            Integer referenceNo,
            Long knowledgeBaseId,
            Long fileId,
            Long chunkId,
            Integer chunkIndex,
            String titlePath,
            BigDecimal score,
            String contentPreview,
            DocumentChunk chunk,
            KnowledgeFile file,
            String content
    ) {
    }
}
