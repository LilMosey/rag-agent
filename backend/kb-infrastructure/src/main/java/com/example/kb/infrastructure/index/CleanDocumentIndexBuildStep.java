package com.example.kb.infrastructure.index;

import com.example.kb.application.port.DocumentCleaner;
import com.example.kb.domain.model.KnowledgeFile;
import com.example.kb.domain.model.KnowledgeFileIndexTask;
import com.example.kb.domain.model.ParsedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class CleanDocumentIndexBuildStep implements IndexBuildStep {

    private static final Logger log = LoggerFactory.getLogger(CleanDocumentIndexBuildStep.class);

    private final DocumentCleaner documentCleaner;

    public CleanDocumentIndexBuildStep(DocumentCleaner documentCleaner) {
        this.documentCleaner = documentCleaner;
    }

    @Override
    public String name() {
        return "CLEAN_DOCUMENT";
    }

    @Override
    public void execute(IndexBuildContext context) {
        KnowledgeFileIndexTask task = context.task();
        KnowledgeFile file = context.file();
        ParsedDocument parsedDocument = context.parsedDocument();
        if (parsedDocument == null) {
            throw new IllegalStateException("文档清洗步骤缺少 parsedDocument。");
        }
        log.info("索引步骤执行入参: step={}, taskId={}, fileId={}, sectionCount={}",
                name(), task.id(), file.id(), parsedDocument.sections().size());
        ParsedDocument cleanedDocument = documentCleaner.clean(parsedDocument);
        context.cleanedDocument(cleanedDocument);
        log.info("索引步骤执行出参: step={}, taskId={}, fileId={}, sectionCount={}",
                name(), task.id(), file.id(), cleanedDocument.sections().size());
    }
}
