package com.example.kb.infrastructure.chunk;

import com.example.kb.application.port.DocumentChunker;
import com.example.kb.domain.model.ChunkConfig;
import com.example.kb.domain.model.DocumentSection;
import com.example.kb.domain.model.ParsedDocument;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecursiveChunkStrategy {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^。！？!?\\.]+[。！？!?\\.]?");

    private final ChunkContentBuilder contentBuilder;

    public RecursiveChunkStrategy(ChunkContentBuilder contentBuilder) {
        this.contentBuilder = contentBuilder;
    }

    public List<DocumentChunker.ChunkDraft> chunk(ParsedDocument document, ChunkConfig chunkConfig) {
        List<DocumentChunker.ChunkDraft> drafts = new ArrayList<>();
        List<DocumentSection> sections = orderedSections(document);
        for (DocumentSection section : sections) {
            appendSectionChunks(document, section, chunkConfig, drafts);
        }
        return drafts;
    }

    private void appendSectionChunks(
            ParsedDocument document,
            DocumentSection section,
            ChunkConfig chunkConfig,
            List<DocumentChunker.ChunkDraft> drafts
    ) {
        String body = nullToEmpty(section.content());
        String sectionContent = contentBuilder.build(document, section, body);
        if (sectionContent.length() <= chunkConfig.chunkSize()) {
            drafts.add(toDraft(document, section, drafts.size(), sectionContent, 0, sectionContent.length()));
            return;
        }

        int bodyBudget = Math.max(1, chunkConfig.chunkSize() - contentBuilder.prefixLength(document, section));
        List<TextSpan> spans = applyOverlap(body, paragraphSpans(body, bodyBudget), chunkConfig.chunkOverlap(), bodyBudget);
        for (TextSpan span : spans) {
            String content = contentBuilder.build(document, section, span.text());
            int startOffset = contentBuilder.prefixLength(document, section) + span.startOffset();
            int endOffset = contentBuilder.prefixLength(document, section) + span.endOffset();
            drafts.add(toDraft(document, section, drafts.size(), content, startOffset, endOffset));
        }
    }

    private List<TextSpan> paragraphSpans(String body, int bodyBudget) {
        List<TextSpan> spans = new ArrayList<>();
        List<TextSpan> paragraphs = splitParagraphs(body);
        TextSpan current = null;
        for (TextSpan paragraph : paragraphs) {
            if (paragraph.text().length() > bodyBudget) {
                if (current != null) {
                    spans.add(current);
                    current = null;
                }
                spans.addAll(sentenceSpans(paragraph, bodyBudget));
            } else if (current == null) {
                current = paragraph;
            } else {
                int combinedLength = paragraph.endOffset() - current.startOffset();
                if (combinedLength <= bodyBudget) {
                    current = new TextSpan(body.substring(current.startOffset(), paragraph.endOffset()), current.startOffset(), paragraph.endOffset());
                } else {
                    spans.add(current);
                    current = paragraph;
                }
            }
        }
        if (current != null) {
            spans.add(current);
        }
        return spans;
    }

    private List<TextSpan> sentenceSpans(TextSpan paragraph, int bodyBudget) {
        List<TextSpan> spans = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(paragraph.text());
        TextSpan current = null;
        while (matcher.find()) {
            String sentence = matcher.group();
            if (sentence.isBlank()) {
                continue;
            }
            int sentenceStart = paragraph.startOffset() + matcher.start();
            int sentenceEnd = paragraph.startOffset() + matcher.end();
            TextSpan sentenceSpan = new TextSpan(sentence, sentenceStart, sentenceEnd);
            if (sentence.length() > bodyBudget) {
                if (current != null) {
                    spans.add(current);
                    current = null;
                }
                spans.addAll(characterSpans(sentenceSpan, bodyBudget));
            } else if (current == null) {
                current = sentenceSpan;
            } else {
                int combinedLength = sentenceEnd - current.startOffset();
                if (combinedLength <= bodyBudget) {
                    current = new TextSpan(paragraph.text().substring(current.startOffset() - paragraph.startOffset(), matcher.end()), current.startOffset(), sentenceEnd);
                } else {
                    spans.add(current);
                    current = sentenceSpan;
                }
            }
        }
        if (current != null) {
            spans.add(current);
        }
        if (spans.isEmpty()) {
            spans.addAll(characterSpans(paragraph, bodyBudget));
        }
        return spans;
    }

    private List<TextSpan> characterSpans(TextSpan span, int bodyBudget) {
        List<TextSpan> spans = new ArrayList<>();
        int startOffset = span.startOffset();
        while (startOffset < span.endOffset()) {
            int endOffset = Math.min(startOffset + bodyBudget, span.endOffset());
            int relativeStart = startOffset - span.startOffset();
            int relativeEnd = endOffset - span.startOffset();
            spans.add(new TextSpan(span.text().substring(relativeStart, relativeEnd), startOffset, endOffset));
            startOffset = endOffset;
        }
        return spans;
    }

    private List<TextSpan> applyOverlap(String body, List<TextSpan> spans, int chunkOverlap, int bodyBudget) {
        if (spans.size() <= 1 || chunkOverlap <= 0) {
            return spans;
        }
        List<TextSpan> overlappedSpans = new ArrayList<>();
        for (int index = 0; index < spans.size(); index++) {
            TextSpan span = spans.get(index);
            if (index == 0) {
                overlappedSpans.add(span);
                continue;
            }
            int maxOverlap = Math.min(chunkOverlap, Math.max(0, bodyBudget - span.text().length()));
            int startOffset = Math.max(0, span.startOffset() - maxOverlap);
            String text = body.substring(startOffset, span.endOffset());
            overlappedSpans.add(new TextSpan(text, startOffset, span.endOffset()));
        }
        return overlappedSpans;
    }

    private List<TextSpan> splitParagraphs(String body) {
        List<TextSpan> paragraphs = new ArrayList<>();
        int startOffset = 0;
        String[] parts = body.split("\\n\\s*\\n", -1);
        for (String part : parts) {
            int foundOffset = body.indexOf(part, startOffset);
            if (foundOffset < 0) {
                foundOffset = startOffset;
            }
            int endOffset = foundOffset + part.length();
            if (!part.isBlank()) {
                paragraphs.add(new TextSpan(part, foundOffset, endOffset));
            }
            startOffset = endOffset;
        }
        if (paragraphs.isEmpty() && !body.isBlank()) {
            paragraphs.add(new TextSpan(body, 0, body.length()));
        }
        return paragraphs;
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record TextSpan(String text, int startOffset, int endOffset) {
    }
}
