package com.example.kb.api.controller;

import com.example.kb.api.dto.ApiResponse;
import com.example.kb.api.dto.KnowledgeBaseDtos;
import com.example.kb.application.service.KnowledgeBaseService;
import com.example.kb.domain.model.KnowledgeBase;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseDtos.Response> create(@Valid @RequestBody KnowledgeBaseDtos.CreateRequest request) {
        log.info("知识库创建接口入参: name={}, description={}", request.name(), request.description());
        KnowledgeBase knowledgeBase = knowledgeBaseService.create(request.name(), request.description());
        KnowledgeBaseDtos.Response response = KnowledgeBaseDtos.Response.from(knowledgeBase);
        log.info("知识库创建接口出参: id={}, name={}", response.id(), response.name());
        return ApiResponse.ok(response);
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseDtos.Response>> list() {
        log.info("知识库列表接口入参: none");
        List<KnowledgeBaseDtos.Response> responses = knowledgeBaseService.list().stream()
                .map(KnowledgeBaseDtos.Response::from)
                .toList();
        log.info("知识库列表接口出参: count={}", responses.size());
        return ApiResponse.ok(responses);
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseDtos.Response> get(@PathVariable("id") Long id) {
        log.info("知识库详情接口入参: id={}", id);
        KnowledgeBase knowledgeBase = knowledgeBaseService.get(id);
        KnowledgeBaseDtos.Response response = KnowledgeBaseDtos.Response.from(knowledgeBase);
        log.info("知识库详情接口出参: id={}, name={}", response.id(), response.name());
        return ApiResponse.ok(response);
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseDtos.Response> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody KnowledgeBaseDtos.UpdateRequest request
    ) {
        log.info("知识库更新接口入参: id={}, name={}, description={}", id, request.name(), request.description());
        KnowledgeBase knowledgeBase = knowledgeBaseService.update(id, request.name(), request.description());
        KnowledgeBaseDtos.Response response = KnowledgeBaseDtos.Response.from(knowledgeBase);
        log.info("知识库更新接口出参: id={}, name={}", response.id(), response.name());
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        log.info("知识库删除接口入参: id={}", id);
        knowledgeBaseService.delete(id);
        log.info("知识库删除接口出参: id={}, deleted=true", id);
        return ApiResponse.ok(null);
    }
}
