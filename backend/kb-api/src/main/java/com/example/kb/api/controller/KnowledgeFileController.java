package com.example.kb.api.controller;

import com.example.kb.api.dto.ApiResponse;
import com.example.kb.api.dto.KnowledgeFileDtos;
import com.example.kb.application.service.KnowledgeFileService;
import com.example.kb.domain.model.KnowledgeFile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases/{kbId}/files")
public class KnowledgeFileController {

    private final KnowledgeFileService knowledgeFileService;

    public KnowledgeFileController(KnowledgeFileService knowledgeFileService) {
        this.knowledgeFileService = knowledgeFileService;
    }

    @PostMapping
    public ApiResponse<KnowledgeFileDtos.Response> upload(
            @PathVariable Long kbId,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        KnowledgeFile knowledgeFile = knowledgeFileService.upload(
                kbId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize()
        );
        return ApiResponse.ok(KnowledgeFileDtos.Response.from(knowledgeFile));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeFileDtos.Response>> list(
            @PathVariable Long kbId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<KnowledgeFileDtos.Response> responses = knowledgeFileService.search(kbId, keyword, status, page, size).stream()
                .map(KnowledgeFileDtos.Response::from)
                .toList();
        return ApiResponse.ok(responses);
    }

    @GetMapping("/{fileId}")
    public ApiResponse<KnowledgeFileDtos.Response> get(@PathVariable Long kbId, @PathVariable Long fileId) {
        KnowledgeFile knowledgeFile = knowledgeFileService.get(kbId, fileId);
        return ApiResponse.ok(KnowledgeFileDtos.Response.from(knowledgeFile));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long kbId, @PathVariable Long fileId) {
        KnowledgeFile knowledgeFile = knowledgeFileService.get(kbId, fileId);
        InputStream inputStream = knowledgeFileService.download(kbId, fileId);
        MediaType mediaType = knowledgeFile.contentType() == null || knowledgeFile.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(knowledgeFile.contentType());
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(knowledgeFile.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(new InputStreamResource(inputStream));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(@PathVariable Long kbId, @PathVariable Long fileId) {
        knowledgeFileService.delete(kbId, fileId);
        return ApiResponse.ok(null);
    }
}
