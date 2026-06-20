package com.example.kb.infrastructure.index;

import com.example.kb.domain.model.DocumentChunk;
import com.example.kb.domain.model.KnowledgeFile;
import com.example.kb.domain.model.KnowledgeFileIndexTask;
import com.example.kb.domain.model.ParsedDocument;

import java.util.List;

public class IndexBuildContext {

    private final KnowledgeFileIndexTask task;
    private final KnowledgeFile file;
    private ParsedDocument parsedDocument;
    private ParsedDocument cleanedDocument;
    private List<DocumentChunk> chunks = List.of();

    public IndexBuildContext(KnowledgeFileIndexTask task, KnowledgeFile file) {
        this.task = task;
        this.file = file;
    }

    public KnowledgeFileIndexTask task() {
        return task;
    }

    public KnowledgeFile file() {
        return file;
    }

    public ParsedDocument parsedDocument() {
        return parsedDocument;
    }

    public void parsedDocument(ParsedDocument parsedDocument) {
        this.parsedDocument = parsedDocument;
    }

    public ParsedDocument cleanedDocument() {
        return cleanedDocument;
    }

    public void cleanedDocument(ParsedDocument cleanedDocument) {
        this.cleanedDocument = cleanedDocument;
    }

    public List<DocumentChunk> chunks() {
        return chunks;
    }

    public void chunks(List<DocumentChunk> chunks) {
        this.chunks = chunks == null ? List.of() : chunks;
    }
}
