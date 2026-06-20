package com.example.kb.infrastructure.index;

import com.example.kb.application.port.DocumentParseCommand;
import com.example.kb.application.port.DocumentParser;
import com.example.kb.application.port.DocumentParserRegistry;
import com.example.kb.application.port.ObjectStorage;
import com.example.kb.domain.model.KnowledgeFile;
import com.example.kb.domain.model.KnowledgeFileIndexTask;
import com.example.kb.domain.model.ParsedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@Order(10)
public class ParseDocumentIndexBuildStep implements IndexBuildStep {

    private static final Logger log = LoggerFactory.getLogger(ParseDocumentIndexBuildStep.class);

    private final ObjectStorage objectStorage;
    private final DocumentParserRegistry parserRegistry;

    public ParseDocumentIndexBuildStep(
            ObjectStorage objectStorage,
            DocumentParserRegistry parserRegistry
    ) {
        this.objectStorage = objectStorage;
        this.parserRegistry = parserRegistry;
    }

    @Override
    public String name() {
        return "PARSE_DOCUMENT";
    }

    @Override
    public void execute(IndexBuildContext context) {
        KnowledgeFileIndexTask task = context.task();
        KnowledgeFile file = context.file();
        log.info("索引步骤执行入参: step={}, taskId={}, fileId={}, fileType={}",
                name(), task.id(), file.id(), file.fileType());
        DocumentParser parser = parserRegistry.getParser(file.fileType());
        try (InputStream inputStream = objectStorage.getObject(file.storageBucket(), file.storageObjectKey())) {
            DocumentParseCommand command = new DocumentParseCommand(
                    file.knowledgeBaseId(),
                    file.id(),
                    file.originalFilename(),
                    file.fileType(),
                    file.contentType(),
                    inputStream
            );
            ParsedDocument parsedDocument = parser.parse(command);
            context.parsedDocument(parsedDocument);
            log.info("索引步骤执行出参: step={}, taskId={}, fileId={}, title={}, sectionCount={}",
                    name(), task.id(), file.id(), parsedDocument.title(), parsedDocument.sections().size());
        } catch (Exception exception) {
            log.error("索引步骤执行异常: step={}, taskId={}, fileId={}",
                    name(), task.id(), file.id(), exception);
            throw new IllegalStateException("文档解析步骤失败: " + exception.getMessage(), exception);
        }
    }
}
