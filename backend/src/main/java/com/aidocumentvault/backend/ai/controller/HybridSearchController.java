package com.aidocumentvault.backend.ai.controller;

import com.aidocumentvault.backend.ai.dto.HybridSearchRequest;
import com.aidocumentvault.backend.ai.dto.HybridSearchResponse;
import com.aidocumentvault.backend.ai.service.HybridSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HybridSearchController {

    private final HybridSearchService hybridSearchService;

    @PostMapping("/api/search/hybrid")
    public ResponseEntity<HybridSearchResponse> search( @Valid @RequestBody HybridSearchRequest request) {

        HybridSearchResponse response = hybridSearchService.search(request);

        return ResponseEntity.ok(response);
    }
}
