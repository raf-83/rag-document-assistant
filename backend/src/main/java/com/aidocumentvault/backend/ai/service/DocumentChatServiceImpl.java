package com.aidocumentvault.backend.ai.service;

import com.aidocumentvault.backend.ai.chat.ChatCompletionClient;
import com.aidocumentvault.backend.ai.chat.model.ChatCompletionResult;
import com.aidocumentvault.backend.ai.chat.model.ChatMessage;
import com.aidocumentvault.backend.ai.dto.ChatSourceChunkResponse;
import com.aidocumentvault.backend.ai.dto.DocumentChatRequest;
import com.aidocumentvault.backend.ai.dto.DocumentChatResponse;
import com.aidocumentvault.backend.ai.exception.DocumentChatNotAvailableException;
import com.aidocumentvault.backend.ai.prompt.DocumentChatPromptBuilder;
import com.aidocumentvault.backend.ai.retrieval.context.DocumentChunkRetrievalService;
import com.aidocumentvault.backend.ai.retrieval.model.RetrievedDocumentChunk;
import com.aidocumentvault.backend.document.entity.Document;
import com.aidocumentvault.backend.document.entity.DocumentStatus;
import com.aidocumentvault.backend.document.exception.DocumentNotFoundException;
import com.aidocumentvault.backend.document.repository.DocumentRepository;
import com.aidocumentvault.backend.security.context.CurrentUserProvider;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentChatServiceImpl implements DocumentChatService {

    private final CurrentUserProvider currentUserProvider;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRetrievalService documentChunkRetrievalService;
    private final DocumentChatPromptBuilder documentChatPromptBuilder;
    private final ChatCompletionClient chatCompletionClient;

    @Override
    public DocumentChatResponse askQuestion(UUID documentId, DocumentChatRequest request) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        Document document = documentRepository.findByIdAndOwnerId(documentId, currentUserId)
            .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        if (document.getStatus() != DocumentStatus.READY) {
            throw new DocumentChatNotAvailableException(
                "Document is not ready for chat. Current status: " + document.getStatus()
            );
        }

        List<RetrievedDocumentChunk> retrievedChunks =
            documentChunkRetrievalService.retrieveRelevantChunks(
                document.getId(),
                currentUserId,
                request.getQuestion()
            );

        if (retrievedChunks.isEmpty()) {
            return DocumentChatResponse.noContext(
                document.getId(),
                "Nie znalazłem w dokumencie fragmentów, które pozwalają odpowiedzieć na to pytanie."
            );
        }

        List<ChatMessage> messages = documentChatPromptBuilder.build(request.getQuestion(), retrievedChunks);

        ChatCompletionResult chatCompletionResult = chatCompletionClient.complete(messages);

        List<ChatSourceChunkResponse> sources = retrievedChunks.stream()
            .map(this::mapToSourceResponse)
            .toList();

        return DocumentChatResponse.withContext(
            document.getId(),
            chatCompletionResult.content(),
            sources
        );
    }

    private ChatSourceChunkResponse mapToSourceResponse(
        RetrievedDocumentChunk chunk
    ) {
        return new ChatSourceChunkResponse(
            chunk.chunkId(),
            chunk.chunkIndex(),
            chunk.startCharIndex(),
            chunk.endCharIndex(),
            chunk.score()
        );
    }
}
