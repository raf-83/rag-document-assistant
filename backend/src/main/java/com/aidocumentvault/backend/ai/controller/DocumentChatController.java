package com.aidocumentvault.backend.ai.controller;

import com.aidocumentvault.backend.ai.dto.DocumentChatRequest;
import com.aidocumentvault.backend.ai.dto.DocumentChatResponse;
import com.aidocumentvault.backend.ai.service.DocumentChatService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DocumentChatController {

    private final DocumentChatService documentChatService;

    @PostMapping("/api/documents/{documentId}/chat")
    public ResponseEntity<DocumentChatResponse> askQuestion(
            @PathVariable UUID documentId,
            @Valid @RequestBody DocumentChatRequest request
    ) {
        DocumentChatResponse response = documentChatService.askQuestion(documentId, request);

        return ResponseEntity.ok(response);
    }
}
