package com.example.kb.api.controller;

import com.example.kb.api.dto.ApiResponse;
import com.example.kb.api.dto.KnowledgeFileDtos;
import com.example.kb.application.service.KnowledgeFileService;
import com.example.kb.domain.model.ChunkStrategy;
import com.example.kb.domain.model.KnowledgeFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFileController.class);

    private final KnowledgeFileService knowledgeFileService;

    public KnowledgeFileController(KnowledgeFileService knowledgeFileService) {
        this.knowledgeFileService = knowledgeFileService;
    }

    @PostMapping
    public ApiResponse<KnowledgeFileDtos.Response> upload(
            @PathVariable("kbId") Long kbId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "chunkStrategy", required = false) ChunkStrategy chunkStrategy,
            @RequestParam(name = "chunkSize", required = false) Integer chunkSize,
            @RequestParam(name = "chunkOverlap", required = false) Integer chunkOverlap
    ) throws IOException {
        log.info("文件上传接口入参: kbId={}, originalFilename={}, contentType={}, size={}, chunkStrategy={}, chunkSize={}, chunkOverlap={}",
                kbId, file.getOriginalFilename(), file.getContentType(), file.getSize(), chunkStrategyLogName(chunkStrategy), chunkSize, chunkOverlap);
        KnowledgeFile knowledgeFile = knowledgeFileService.upload(
                kbId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize(),
                chunkStrategy,
                chunkSize,
                chunkOverlap
        );
        KnowledgeFileDtos.Response response = KnowledgeFileDtos.Response.from(knowledgeFile);
        log.info("文件上传接口出参: id={}, filename={}, status={}", response.id(), response.originalFilename(), response.fileStatus());
        return ApiResponse.ok(response);
    }

    private String chunkStrategyLogName(ChunkStrategy chunkStrategy) {
        if (chunkStrategy == null) {
            return "未传入，使用默认策略";
        }
        return chunkStrategy.logName();
    }

    @GetMapping
    public ApiResponse<List<KnowledgeFileDtos.Response>> list(
            @PathVariable("kbId") Long kbId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        log.info("文件列表接口入参: kbId={}, keyword={}, status={}, page={}, size={}", kbId, keyword, status, page, size);
        List<KnowledgeFileDtos.Response> responses = knowledgeFileService.search(kbId, keyword, status, page, size).stream()
                .map(KnowledgeFileDtos.Response::from)
                .toList();
        log.info("文件列表接口出参: count={}", responses.size());
        return ApiResponse.ok(responses);
    }

    @GetMapping("/{fileId}")
    public ApiResponse<KnowledgeFileDtos.Response> get(
            @PathVariable("kbId") Long kbId,
            @PathVariable("fileId") Long fileId
    ) {
        log.info("文件详情接口入参: kbId={}, fileId={}", kbId, fileId);
        KnowledgeFile knowledgeFile = knowledgeFileService.get(kbId, fileId);
        KnowledgeFileDtos.Response response = KnowledgeFileDtos.Response.from(knowledgeFile);
        log.info("文件详情接口出参: id={}, filename={}, status={}", response.id(), response.originalFilename(), response.fileStatus());
        return ApiResponse.ok(response);
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable("kbId") Long kbId,
            @PathVariable("fileId") Long fileId
    ) {
        log.info("文件下载接口入参: kbId={}, fileId={}", kbId, fileId);
        KnowledgeFile knowledgeFile = knowledgeFileService.get(kbId, fileId);
        InputStream inputStream = knowledgeFileService.download(kbId, fileId);
        MediaType mediaType = knowledgeFile.contentType() == null || knowledgeFile.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(knowledgeFile.contentType());
        if (knowledgeFile.contentType() == null || knowledgeFile.contentType().isBlank()) {
            log.info("文件下载接口分支: contentType 为空, 使用默认 application/octet-stream, fileId={}", fileId);
        } else {
            log.info("文件下载接口分支: 使用文件 contentType={}, fileId={}", knowledgeFile.contentType(), fileId);
        }
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(knowledgeFile.originalFilename(), StandardCharsets.UTF_8)
                .build();
        log.info("文件下载接口出参: filename={}, mediaType={}", knowledgeFile.originalFilename(), mediaType);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(new InputStreamResource(inputStream));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(
            @PathVariable("kbId") Long kbId,
            @PathVariable("fileId") Long fileId
    ) {
        log.info("文件删除接口入参: kbId={}, fileId={}", kbId, fileId);
        knowledgeFileService.delete(kbId, fileId);
        log.info("文件删除接口出参: kbId={}, fileId={}, deleted=true", kbId, fileId);
        return ApiResponse.ok(null);
    }
}
