package com.example.kb.infrastructure.chunk;

import com.example.kb.application.port.DocumentChunker;
import com.example.kb.domain.model.ChunkConfig;
import com.example.kb.domain.model.DocumentSection;
import com.example.kb.domain.model.ParsedDocument;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FixedSizeChunkStrategy {

    private static final String SECTION_SEPARATOR = "\n\n";

    private final ChunkContentBuilder contentBuilder;

    public FixedSizeChunkStrategy(ChunkContentBuilder contentBuilder) {
        this.contentBuilder = contentBuilder;
    }

    public List<DocumentChunker.ChunkDraft> chunk(ParsedDocument document, ChunkConfig chunkConfig) {
        List<SectionRange> ranges = new ArrayList<>();
        StringBuilder stream = new StringBuilder();
        List<DocumentSection> sections = orderedSections(document);
        for (DocumentSection section : sections) {
            if (stream.length() > 0) {
                stream.append(SECTION_SEPARATOR);
            }
            int startOffset = stream.length();
            String sectionContent = contentBuilder.build(document, section);
            stream.append(sectionContent);
            ranges.add(new SectionRange(section, startOffset, stream.length()));
        }
        return splitStream(document, chunkConfig, stream.toString(), ranges);
    }

    private List<DocumentChunker.ChunkDraft> splitStream(
            ParsedDocument document,
            ChunkConfig chunkConfig,
            String stream,
            List<SectionRange> ranges
    ) {
        List<DocumentChunker.ChunkDraft> drafts = new ArrayList<>();
        int startOffset = 0;
        while (startOffset < stream.length()) {
            int endOffset = Math.min(startOffset + chunkConfig.chunkSize(), stream.length());
            String content = stream.substring(startOffset, endOffset);
            SectionRange range = findRange(ranges, startOffset);
            DocumentSection section = range == null ? null : range.section();
            drafts.add(toDraft(document, section, drafts.size(), content, startOffset, endOffset));
            if (endOffset >= stream.length()) {
                break;
            }
            startOffset = Math.max(endOffset - chunkConfig.chunkOverlap(), startOffset + 1);
        }
        return drafts;
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
                section == null ? null : section.id(),
                section == null ? null : section.parentId(),
                chunkIndex,
                contentBuilder.titlePath(document, section),
                content,
                ChunkHash.sha256Hex(content),
                content.length(),
                startOffset,
                endOffset
        );
    }

    private SectionRange findRange(List<SectionRange> ranges, int offset) {
        for (SectionRange range : ranges) {
            if (offset >= range.startOffset() && offset < range.endOffset()) {
                return range;
            }
        }
        if (ranges.isEmpty()) {
            return null;
        }
        return ranges.get(ranges.size() - 1);
    }

    private List<DocumentSection> orderedSections(ParsedDocument document) {
        if (document.sections() == null || document.sections().isEmpty()) {
            return List.of();
        }
        List<DocumentSection> sections = new ArrayList<>(document.sections());
        sections.sort(Comparator.comparing(DocumentSection::orderIndex, Comparator.nullsLast(Integer::compareTo)));
        return sections;
    }

    private record SectionRange(DocumentSection section, int startOffset, int endOffset) {
    }
}
