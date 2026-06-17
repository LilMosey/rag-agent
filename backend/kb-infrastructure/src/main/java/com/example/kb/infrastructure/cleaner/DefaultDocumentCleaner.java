package com.example.kb.infrastructure.cleaner;

import com.example.kb.application.port.DocumentCleaner;
import com.example.kb.domain.model.DocumentSection;
import com.example.kb.domain.model.ParsedDocument;
import com.example.kb.infrastructure.parser.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DefaultDocumentCleaner implements DocumentCleaner {

    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentCleaner.class);
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)]\\(([^)]*)\\)");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]*)]\\(([^)]*)\\)");
    private static final String CODE_FENCE = "```";

    @Override
    public ParsedDocument clean(ParsedDocument document) {
        log.info("文档清洗入参: knowledgeBaseId={}, fileId={}, filename={}, sectionCount={}",
                document.knowledgeBaseId(), document.fileId(), document.filename(), document.sections().size());
        try {
            List<DocumentSection> cleanedSections = cleanSections(document.sections());
            ParsedDocument cleanedDocument = new ParsedDocument(
                    document.knowledgeBaseId(),
                    document.fileId(),
                    document.filename(),
                    document.fileType(),
                    cleanTitle(document.title()),
                    cleanedSections,
                    copyMetadata(document.metadata())
            );
            log.info("文档清洗出参: fileId={}, beforeSectionCount={}, afterSectionCount={}",
                    document.fileId(), document.sections().size(), cleanedSections.size());
            return cleanedDocument;
        } catch (Exception exception) {
            log.error("文档清洗异常: fileId={}, filename={}", document.fileId(), document.filename(), exception);
            throw new IllegalStateException("文档清洗失败: " + exception.getMessage(), exception);
        }
    }

    private List<DocumentSection> cleanSections(List<DocumentSection> sections) {
        List<DocumentSection> cleanedSections = new ArrayList<>();
        List<DocumentSection> orderedSections = new ArrayList<>(sections);
        orderedSections.sort(Comparator.comparing(DocumentSection::orderIndex, Comparator.nullsLast(Integer::compareTo)));
        for (DocumentSection section : orderedSections) {
            DocumentSection cleanedSection = cleanSection(section);
            if (isEmptySection(cleanedSection)) {
                log.info("文档清洗分支: 删除空章节, sectionId={}, orderIndex={}", section.id(), section.orderIndex());
            } else {
                cleanedSections.add(cleanedSection);
            }
        }
        return cleanedSections;
    }

    private DocumentSection cleanSection(DocumentSection section) {
        String cleanedTitle = cleanTitle(section.title());
        String cleanedContent = cleanContent(section.content());
        Map<String, String> metadata = copyMetadata(section.metadata());
        return new DocumentSection(
                section.id(),
                section.parentId(),
                section.level(),
                cleanedTitle,
                cleanedContent,
                section.orderIndex(),
                metadata
        );
    }

    private String cleanTitle(String title) {
        String cleanedTitle = TextNormalizer.removeControlChars(title).trim();
        return cleanedTitle.replaceAll("\\s+", " ");
    }

    private String cleanContent(String content) {
        String normalized = TextNormalizer.normalizeLineBreaks(TextNormalizer.removeControlChars(content));
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean inCodeBlock = false;
        int outsideBlankLineCount = 0;
        String[] lines = normalized.split("\n", -1);
        for (String line : lines) {
            String currentLine = line;
            if (currentLine.trim().startsWith(CODE_FENCE)) {
                inCodeBlock = !inCodeBlock;
                outsideBlankLineCount = 0;
                builder.append(currentLine.stripTrailing()).append('\n');
            } else if (inCodeBlock) {
                builder.append(currentLine).append('\n');
            } else {
                String cleanedLine = cleanMarkdownInline(currentLine).stripTrailing();
                if (cleanedLine.isBlank()) {
                    outsideBlankLineCount++;
                    if (builder.length() > 0 && outsideBlankLineCount <= 2) {
                        builder.append('\n');
                    }
                } else {
                    outsideBlankLineCount = 0;
                    builder.append(cleanedLine).append('\n');
                }
            }
        }
        return builder.toString().strip();
    }

    private String cleanMarkdownInline(String line) {
        String withoutImageSyntax = replaceImages(line);
        return replaceLinks(withoutImageSyntax);
    }

    private String replaceImages(String line) {
        Matcher matcher = IMAGE_PATTERN.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String alt = matcher.group(1) == null ? "" : matcher.group(1).trim();
            String replacement = alt.isBlank() ? "[图片]" : "[图片: " + alt + "]";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceLinks(String line) {
        Matcher matcher = LINK_PATTERN.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String text = matcher.group(1) == null ? "" : matcher.group(1).trim();
            String url = matcher.group(2) == null ? "" : matcher.group(2).trim();
            String replacement;
            if (text.isBlank()) {
                replacement = url;
            } else if (url.isBlank()) {
                replacement = text;
            } else {
                replacement = text + "（" + url + "）";
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean isEmptySection(DocumentSection section) {
        boolean titleBlank = section.title() == null || section.title().isBlank();
        boolean contentBlank = section.content() == null || section.content().isBlank();
        boolean metadataBlank = section.metadata() == null || section.metadata().isEmpty();
        return titleBlank && contentBlank && metadataBlank;
    }

    private Map<String, String> copyMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(metadata);
    }
}
