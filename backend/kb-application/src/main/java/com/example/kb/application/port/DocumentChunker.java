package com.example.kb.application.port;

import com.example.kb.domain.model.ChunkConfig;
import com.example.kb.domain.model.ParsedDocument;

import java.util.List;

public interface DocumentChunker {

    List<ChunkDraft> chunk(ParsedDocument document, ChunkConfig chunkConfig);

    record ChunkDraft(
            String sectionId,
            String parentSectionId,
            int chunkIndex,
            String titlePath,
            String content,
            String contentHash,
            int contentSize,
            int startOffset,
            int endOffset
    ) {
    }
}
