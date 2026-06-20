package com.example.kb.infrastructure.rag;

import com.example.kb.application.port.QueryRewriteGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;

public class AgentScopeQueryRewriteGenerator implements QueryRewriteGenerator {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeQueryRewriteGenerator.class);

    private final RagProperties properties;
    private final RagPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final OpenAIChatModel chatModel;

    public AgentScopeQueryRewriteGenerator(
            RagProperties properties,
            RagPromptBuilder promptBuilder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.chatModel = OpenAIChatModel.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .modelName(properties.answerModel())
                .stream(false)
                .build();
    }

    @Override
    public RewriteResult rewrite(RewriteCommand command) {
        log.info("Query 改写入参: questionLength={}, historyCount={}, provider={}, model={}",
                command.userQuestion().length(), command.recentMessages().size(), properties.provider(), properties.answerModel());
        try {
            String prompt = promptBuilder.buildQueryRewritePrompt(
                    command.userQuestion(),
                    command.queryIntent(),
                    command.recentMessages()
            );
            String responseText = callModel(prompt);
            LlmRewriteResponse response = objectMapper.readValue(cleanJson(responseText), LlmRewriteResponse.class);
            String rewrittenQuery = response.rewrittenQuery() == null || response.rewrittenQuery().isBlank()
                    ? command.userQuestion()
                    : response.rewrittenQuery().trim();
            Boolean changed = response.changed() != null && response.changed();
            RewriteResult result = new RewriteResult(
                    rewrittenQuery,
                    changed,
                    response.reason(),
                    properties.provider(),
                    properties.answerModel()
            );
            log.info("Query 改写出参: changed={}, rewrittenLength={}", result.changed(), result.rewrittenQuery().length());
            return result;
        } catch (Exception exception) {
            log.error("Query 改写异常: questionLength={}", command.userQuestion().length(), exception);
            throw new IllegalStateException("Query 改写失败: " + exception.getMessage(), exception);
        }
    }

    private String callModel(String prompt) {
        Msg message = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(prompt)
                .build();
        GenerateOptions generateOptions = GenerateOptions.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .modelName(properties.answerModel())
                .stream(Boolean.FALSE)
                .temperature(0.0D)
                .build();
        Flux<ChatResponse> responseFlux = chatModel.stream(List.of(message), List.of(), generateOptions);
        List<ChatResponse> responses = responseFlux.collectList().block();
        return extractText(responses);
    }

    private String extractText(List<ChatResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            throw new IllegalStateException("LLM 返回为空。");
        }
        StringBuilder builder = new StringBuilder();
        for (ChatResponse response : responses) {
            if (response.getContent() == null) {
                continue;
            }
            for (ContentBlock contentBlock : response.getContent()) {
                if (contentBlock instanceof TextBlock textBlock) {
                    builder.append(textBlock.getText());
                }
            }
        }
        String text = builder.toString().trim();
        if (text.isBlank()) {
            throw new IllegalStateException("LLM 文本内容为空。");
        }
        return text;
    }

    private String cleanJson(String responseText) {
        String text = responseText.trim();
        if (text.startsWith("```json")) {
            text = text.substring("```json".length()).trim();
        } else if (text.startsWith("```")) {
            text = text.substring("```".length()).trim();
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - "```".length()).trim();
        }
        return text;
    }

    private record LlmRewriteResponse(
            String rewrittenQuery,
            Boolean changed,
            String reason
    ) {
    }
}
