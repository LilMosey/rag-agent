package com.example.kb.infrastructure.rag;

import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankOutput;
import com.alibaba.dashscope.rerank.TextReRankParam;
import com.alibaba.dashscope.rerank.TextReRankResult;
import com.alibaba.dashscope.utils.Constants;
import com.example.kb.application.port.RerankGenerator;
import com.example.kb.application.service.RagRetrievalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DashScopeRerankGenerator implements RerankGenerator {

    private static final Logger log = LoggerFactory.getLogger(DashScopeRerankGenerator.class);

    private final RagRetrievalProperties ragRetrievalProperties;
    private final TextReRank textReRank;

    public DashScopeRerankGenerator(RagRetrievalProperties ragRetrievalProperties) {
        this.ragRetrievalProperties = ragRetrievalProperties;
        if (!ragRetrievalProperties.safeRerankApiKey().isBlank()) {
            Constants.apiKey = ragRetrievalProperties.safeRerankApiKey();
        }
        if (!ragRetrievalProperties.safeRerankBaseUrl().isBlank()) {
            Constants.baseHttpApiUrl = ragRetrievalProperties.safeRerankBaseUrl();
        }
        this.textReRank = new TextReRank();
    }

    @Override
    public RerankResult rerank(RerankCommand command) {
        log.info("DashScope Rerank 入参: queryLength={}, documentCount={}, topN={}, model={}",
                command.query().length(), command.documents().size(), command.topN(),
                ragRetrievalProperties.safeRerankModel());
        try {
            List<String> documents = command.documents().stream()
                    .map(RerankDocument::content)
                    .toList();
            String apiKey = ragRetrievalProperties.safeRerankApiKey().isBlank()
                    ? null
                    : ragRetrievalProperties.safeRerankApiKey();
            TextReRankParam param = TextReRankParam.builder()
                    .apiKey(apiKey)
                    .model(ragRetrievalProperties.safeRerankModel())
                    .query(command.query())
                    .documents(documents)
                    .topN(command.topN())
                    .returnDocuments(ragRetrievalProperties.isRerankReturnDocuments())
                    .build();
            TextReRankResult result = textReRank.call(param);
            List<RerankItem> items = toRerankItems(command.documents(), result);
            log.info("DashScope Rerank 出参: itemCount={}, provider={}, model={}",
                    items.size(), "dashscope", ragRetrievalProperties.safeRerankModel());
            return new RerankResult(items, "dashscope", ragRetrievalProperties.safeRerankModel());
        } catch (Exception exception) {
            log.error("DashScope Rerank 异常: queryLength={}, documentCount={}",
                    command.query().length(), command.documents().size(), exception);
            throw new IllegalStateException("DashScope Rerank 失败: " + exception.getMessage(), exception);
        }
    }

    private List<RerankItem> toRerankItems(
            List<RerankDocument> documents,
            TextReRankResult result
    ) {
        if (result == null || result.getOutput() == null || result.getOutput().getResults() == null) {
            log.warn("DashScope Rerank 分支: 返回结果为空");
            return List.of();
        }
        List<RerankItem> items = new ArrayList<>();
        int rankNo = 1;
        for (TextReRankOutput.Result item : result.getOutput().getResults()) {
            if (item.getIndex() == null || item.getIndex() < 0 || item.getIndex() >= documents.size()) {
                log.warn("DashScope Rerank 分支: 跳过非法 index, index={}, documentCount={}",
                        item.getIndex(), documents.size());
                continue;
            }
            RerankDocument document = documents.get(item.getIndex());
            BigDecimal score = item.getRelevanceScore() == null
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(item.getRelevanceScore());
            items.add(new RerankItem(document.chunkId(), score, rankNo));
            rankNo++;
        }
        return items;
    }
}
