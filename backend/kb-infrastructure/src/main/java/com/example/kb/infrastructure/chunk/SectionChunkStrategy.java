package com.example.kb.infrastructure.chunk;

import com.example.kb.application.port.DocumentChunker;
import com.example.kb.domain.model.ChunkConfig;
import com.example.kb.domain.model.DocumentSection;
import com.example.kb.domain.model.ParsedDocument;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SectionChunkStrategy {

    private final ChunkContentBuilder contentBuilder;

    public SectionChunkStrategy(ChunkContentBuilder contentBuilder) {
        this.contentBuilder = contentBuilder;
    }

    public List<DocumentChunker.ChunkDraft> chunk(ParsedDocument document, ChunkConfig chunkConfig) {
        List<DocumentChunker.ChunkDraft> drafts = new ArrayList<>();
        List<DocumentSection> sections = orderedSections(document);
        for (DocumentSection section : sections) {
            String sectionContent = contentBuilder.build(document, section);
            appendFixedChunks(document, section, chunkConfig, sectionContent, drafts);
        }
        return drafts;
    }

    private void appendFixedChunks(
            ParsedDocument document,
            DocumentSection section,
            ChunkConfig chunkConfig,
            String sectionContent,
            List<DocumentChunker.ChunkDraft> drafts
    ) {
        int startOffset = 0;
        while (startOffset < sectionContent.length()) {
            int endOffset = Math.min(startOffset + chunkConfig.chunkSize(), sectionContent.length());
            String content = sectionContent.substring(startOffset, endOffset);
            drafts.add(toDraft(document, section, drafts.size(), content, startOffset, endOffset));
            if (endOffset >= sectionContent.length()) {
                break;
            }
            startOffset = Math.max(endOffset - chunkConfig.chunkOverlap(), startOffset + 1);
        }
    }

    private DocumentChunker.ChunkDraft toDraft(
            ParsedDocument document,
            DocumentSection section,
            int chunkIndex,
            String content,
            int startOffset,
            int endOffset
    ) {
        return new DocumentChunker.ChunkDraft(
                section.id(),
                section.parentId(),
                chunkIndex,
                contentBuilder.titlePath(document, section),
                content,
                ChunkHash.sha256Hex(content),
                content.length(),
                startOffset,
                endOffset
        );
    }

    private List<DocumentSection> orderedSections(ParsedDocument document) {
        if (document.sections() == null || document.sections().isEmpty()) {
            return List.of();
        }
        List<DocumentSection> sections = new ArrayList<>(document.sections());
        sections.sort(Comparator.comparing(DocumentSection::orderIndex, Comparator.nullsLast(Integer::compareTo)));
        return sections;
    }
}
