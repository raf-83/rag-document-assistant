package com.aidocumentvault.backend.ai.controller;

import com.aidocumentvault.backend.ai.dto.SemanticSearchRequest;
import com.aidocumentvault.backend.ai.dto.SemanticSearchResponse;
import com.aidocumentvault.backend.ai.service.SemanticSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SemanticSearchController {

    private final SemanticSearchService semanticSearchService;

    @PostMapping("/api/search/semantic")
    public ResponseEntity<SemanticSearchResponse> search(
            @Valid @RequestBody SemanticSearchRequest request
    ) {
        SemanticSearchResponse response = semanticSearchService.search(request);

        return ResponseEntity.ok(response);
    }
}
