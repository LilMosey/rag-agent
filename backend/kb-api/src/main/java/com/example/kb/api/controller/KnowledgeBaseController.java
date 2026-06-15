package com.example.kb.api.controller;

import com.example.kb.api.dto.ApiResponse;
import com.example.kb.api.dto.KnowledgeBaseDtos;
import com.example.kb.application.service.KnowledgeBaseService;
import com.example.kb.domain.model.KnowledgeBase;
import jakarta.validation.Valid;
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

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseDtos.Response> create(@Valid @RequestBody KnowledgeBaseDtos.CreateRequest request) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.create(request.name(), request.description());
        return ApiResponse.ok(KnowledgeBaseDtos.Response.from(knowledgeBase));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseDtos.Response>> list() {
        List<KnowledgeBaseDtos.Response> responses = knowledgeBaseService.list().stream()
                .map(KnowledgeBaseDtos.Response::from)
                .toList();
        return ApiResponse.ok(responses);
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseDtos.Response> get(@PathVariable("id") Long id) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.get(id);
        return ApiResponse.ok(KnowledgeBaseDtos.Response.from(knowledgeBase));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseDtos.Response> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody KnowledgeBaseDtos.UpdateRequest request
    ) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.update(id, request.name(), request.description());
        return ApiResponse.ok(KnowledgeBaseDtos.Response.from(knowledgeBase));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        knowledgeBaseService.delete(id);
        return ApiResponse.ok(null);
    }
}
