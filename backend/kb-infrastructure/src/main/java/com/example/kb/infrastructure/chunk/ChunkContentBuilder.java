package com.example.kb.infrastructure.chunk;

import com.example.kb.domain.model.DocumentSection;
import com.example.kb.domain.model.ParsedDocument;

import java.util.Map;

public class ChunkContentBuilder {

    private static final String TITLE_PATH_KEY = "title_path";
    private static final String TITLE_SEPARATOR = "\n\n";
    private static final String TITLE_PREFIX = "标题路径：";

    public String build(ParsedDocument document, DocumentSection section) {
        return build(document, section, section == null ? "" : section.content());
    }

    public String build(ParsedDocument document, DocumentSection section, String body) {
        return TITLE_PREFIX + titlePath(document, section) + TITLE_SEPARATOR + nullToEmpty(body);
    }

    public String titlePath(ParsedDocument document, DocumentSection section) {
        String metadataTitlePath = metadataValue(section == null ? null : section.metadata(), TITLE_PATH_KEY);
        if (!metadataTitlePath.isBlank()) {
            return metadataTitlePath;
        }
        String sectionTitle = section == null ? "" : nullToEmpty(section.title()).trim();
        if (!sectionTitle.isBlank()) {
            return sectionTitle;
        }
        return nullToEmpty(document == null ? "" : document.filename()).trim();
    }

    public int prefixLength(ParsedDocument document, DocumentSection section) {
        return TITLE_PREFIX.length() + titlePath(document, section).length() + TITLE_SEPARATOR.length();
    }

    private String metadataValue(Map<String, String> metadata, String key) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        String value = metadata.get(key);
        return value == null ? "" : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
