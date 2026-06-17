package com.example.kb.application.port;

import com.example.kb.domain.model.ParsedDocument;

public interface DocumentCleaner {

    ParsedDocument clean(ParsedDocument document);
}
