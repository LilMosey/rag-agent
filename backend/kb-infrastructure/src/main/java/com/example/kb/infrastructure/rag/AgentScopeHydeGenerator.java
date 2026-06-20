package com.example.kb.infrastructure.rag;

import com.example.kb.application.port.HydeGenerator;
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

public class AgentScopeHydeGenerator implements HydeGenerator {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeHydeGenerator.class);

    private final RagProperties properties;
    private final RagPromptBuilder promptBuilder;
    private final OpenAIChatModel chatModel;

    public AgentScopeHydeGenerator(RagProperties properties, RagPromptBuilder promptBuilder) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.chatModel = OpenAIChatModel.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .modelName(properties.answerModel())
                .stream(false)
                .build();
    }

    @Override
    public HydeResult generate(HydeCommand command) {
        log.info("HyDE 生成入参: questionLength={}, provider={}, model={}",
                command.userQuestion().length(), properties.provider(), properties.answerModel());
        try {
            String prompt = promptBuilder.buildHydePrompt(command.userQuestion());
            String responseText = callModel(prompt);
            log.info("HyDE 生成出参: answerLength={}", responseText.length());
            return new HydeResult(responseText, properties.provider(), properties.answerModel());
        } catch (Exception exception) {
            log.error("HyDE 生成异常: questionLength={}", command.userQuestion().length(), exception);
            throw new IllegalStateException("HyDE 生成失败: " + exception.getMessage(), exception);
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
                .temperature(0.2D)
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
}
