package com.example.kb.infrastructure.embedding;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.utils.Constants;
import com.example.kb.application.port.EmbeddingGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DashScopeEmbeddingGenerator implements EmbeddingGenerator {

    private static final Logger log = LoggerFactory.getLogger(DashScopeEmbeddingGenerator.class);

    private final EmbeddingProperties properties;
    private final TextEmbedding textEmbedding;

    public DashScopeEmbeddingGenerator(EmbeddingProperties properties) {
        this.properties = properties;
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            Constants.apiKey = properties.apiKey();
        }
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            Constants.baseHttpApiUrl = properties.baseUrl();
        }
        this.textEmbedding = new TextEmbedding();
    }

    @Override
    public GenerateEmbeddingsResult generate(GenerateEmbeddingsCommand command) {
        log.info("DashScope embedding 入参: textCount={}, model={}", command.texts().size(), properties.model());
        try {
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(properties.model())
                    .texts(command.texts())
                    .build();
            TextEmbeddingResult result = textEmbedding.call(param);
            if (result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
                throw new IllegalStateException("DashScope embedding 返回结果为空。");
            }
            List<TextEmbeddingResultItem> resultItems = result.getOutput().getEmbeddings().stream()
                    .sorted(Comparator.comparing(TextEmbeddingResultItem::getTextIndex))
                    .toList();
            List<EmbeddingItem> items = new ArrayList<>(resultItems.size());
            for (TextEmbeddingResultItem resultItem : resultItems) {
                List<Float> vector = toFloatVector(resultItem.getEmbedding());
                validateDimension(vector);
                items.add(new EmbeddingItem(resultItem.getTextIndex(), vector));
            }
            Integer totalTokens = result.getUsage() == null ? null : result.getUsage().getTotalTokens();
            log.info("DashScope embedding 出参: textCount={}, vectorCount={}, totalTokens={}",
                    command.texts().size(), items.size(), totalTokens);
            return new GenerateEmbeddingsResult(items, properties.provider(), properties.model(), totalTokens);
        } catch (Exception exception) {
            log.error("DashScope embedding 异常: textCount={}, model={}", command.texts().size(), properties.model(), exception);
            throw new IllegalStateException("DashScope embedding 生成失败: " + exception.getMessage(), exception);
        }
    }

    private List<Float> toFloatVector(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalStateException("DashScope embedding 向量为空。");
        }
        List<Float> vector = new ArrayList<>(embedding.size());
        for (Double value : embedding) {
            vector.add(value.floatValue());
        }
        return vector;
    }

    private void validateDimension(List<Float> vector) {
        if (properties.dimension() > 0 && vector.size() != properties.dimension()) {
            throw new IllegalStateException("embedding 维度不匹配，期望=" + properties.dimension() + "，实际=" + vector.size());
        }
    }
}
