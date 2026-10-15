package com.aidocumentvault.backend.ai.service;

import com.aidocumentvault.backend.ai.dto.DocumentChatRequest;
import com.aidocumentvault.backend.ai.dto.DocumentChatResponse;
import java.util.UUID;

public interface DocumentChatService {

    DocumentChatResponse askQuestion(UUID documentId, DocumentChatRequest request);
}
