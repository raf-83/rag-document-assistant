package com.aidocumentvault.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aidocumentvault.backend.ai.chat.ChatCompletionClient;
import com.aidocumentvault.backend.ai.chat.model.ChatCompletionResult;
import com.aidocumentvault.backend.ai.chat.model.ChatMessage;
import com.aidocumentvault.backend.ai.chat.model.ChatTokenUsage;
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
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentChatServiceImplTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRetrievalService documentChunkRetrievalService;

    @Mock
    private DocumentChatPromptBuilder documentChatPromptBuilder;

    @Mock
    private ChatCompletionClient chatCompletionClient;

    @InjectMocks
    private DocumentChatServiceImpl documentChatService;

    @Test
    void shouldReturnNoContextResponseWhenDocumentBelongsToUserAndIsReadyButNoChunksMatch() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Document document = mock(Document.class);

        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(documentRepository.findByIdAndOwnerId(documentId, ownerId))
                .thenReturn(Optional.of(document));
        when(document.getId()).thenReturn(documentId);
        when(document.getStatus()).thenReturn(DocumentStatus.READY);

        DocumentChatRequest request = new DocumentChatRequest(
                "O czym jest ten dokument?"
        );

        when(documentChunkRetrievalService.retrieveRelevantChunks(
                documentId,
                ownerId,
                request.getQuestion()
        )).thenReturn(List.of());

        // when
        DocumentChatResponse response = documentChatService.askQuestion(documentId, request);

        // then
        assertThat(response.documentId()).isEqualTo(documentId);
        assertThat(response.contextFound()).isFalse();
        assertThat(response.sources()).isEmpty();
        assertThat(response.answer()).contains("Nie znalaz");

        verify(currentUserProvider).getCurrentUserId();
        verify(documentRepository).findByIdAndOwnerId(documentId, ownerId);
        verify(documentChunkRetrievalService).retrieveRelevantChunks(
                documentId,
                ownerId,
                request.getQuestion()
        );
        verifyNoInteractions(documentChatPromptBuilder, chatCompletionClient);
    }

    @Test
    void shouldReturnChatCompletionResponseWithSourcesWhenDocumentBelongsToUserAndChunksMatch() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID firstChunkId = UUID.randomUUID();
        UUID secondChunkId = UUID.randomUUID();

        Document document = mock(Document.class);

        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(documentRepository.findByIdAndOwnerId(documentId, ownerId))
                .thenReturn(Optional.of(document));
        when(document.getId()).thenReturn(documentId);
        when(document.getStatus()).thenReturn(DocumentStatus.READY);

        DocumentChatRequest request = new DocumentChatRequest(
                "O czym jest ten dokument?"
        );

        List<RetrievedDocumentChunk> retrievedChunks = List.of(
                new RetrievedDocumentChunk(
                        firstChunkId,
                        0,
                        "Pierwszy znaleziony fragment dokumentu.",
                        10,
                        48,
                        0.91
                ),
                new RetrievedDocumentChunk(
                        secondChunkId,
                        3,
                        "Drugi znaleziony fragment dokumentu.",
                        120,
                        156,
                        0.83
                )
        );

        when(documentChunkRetrievalService.retrieveRelevantChunks(
                documentId,
                ownerId,
                request.getQuestion()
        )).thenReturn(retrievedChunks);

        List<ChatMessage> messages = List.of(
                ChatMessage.system("Instrukcja systemowa"),
                ChatMessage.user("Pytanie i znalezione fragmenty")
        );

        when(documentChatPromptBuilder.build(request.getQuestion(), retrievedChunks))
                .thenReturn(messages);

        ChatCompletionResult chatCompletionResult = new ChatCompletionResult(
                "Dokument opisuje przykladowa tresc.",
                "test-model",
                new ChatTokenUsage(12, 7)
        );

        when(chatCompletionClient.complete(messages)).thenReturn(chatCompletionResult);

        // when
        DocumentChatResponse response = documentChatService.askQuestion(documentId, request);

        // then
        assertThat(response.documentId()).isEqualTo(documentId);
        assertThat(response.contextFound()).isTrue();
        assertThat(response.answer()).isEqualTo(chatCompletionResult.content());
        assertThat(response.sources())
                .extracting(
                        ChatSourceChunkResponse::chunkId,
                        ChatSourceChunkResponse::chunkIndex,
                        ChatSourceChunkResponse::startCharIndex,
                        ChatSourceChunkResponse::endCharIndex,
                        ChatSourceChunkResponse::score
                )
                .containsExactly(
                        tuple(firstChunkId, 0, 10, 48, 0.91),
                        tuple(secondChunkId, 3, 120, 156, 0.83)
                );

        verify(currentUserProvider).getCurrentUserId();
        verify(documentRepository).findByIdAndOwnerId(documentId, ownerId);
        verify(documentChunkRetrievalService).retrieveRelevantChunks(
                documentId,
                ownerId,
                request.getQuestion()
        );
        verify(documentChatPromptBuilder).build(request.getQuestion(), retrievedChunks);
        verify(chatCompletionClient).complete(messages);
    }

    @Test
    void shouldThrowDocumentNotFoundExceptionWhenDocumentDoesNotBelongToUserOrDoesNotExist() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(documentRepository.findByIdAndOwnerId(documentId, ownerId))
                .thenReturn(Optional.empty());

        DocumentChatRequest request = new DocumentChatRequest(
                "O czym jest ten dokument?"
        );

        // when / then
        assertThatThrownBy(() -> documentChatService.askQuestion(documentId, request))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("Document not found");

        verify(currentUserProvider).getCurrentUserId();
        verify(documentRepository).findByIdAndOwnerId(documentId, ownerId);
    }

    @Test
    void shouldThrowDocumentChatNotAvailableExceptionWhenDocumentIsNotReady() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Document document = mock(Document.class);

        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        when(documentRepository.findByIdAndOwnerId(documentId, ownerId))
                .thenReturn(Optional.of(document));
        when(document.getStatus()).thenReturn(DocumentStatus.CHUNKING);

        DocumentChatRequest request = new DocumentChatRequest(
                "O czym jest ten dokument?"
        );

        // when / then
        assertThatThrownBy(() -> documentChatService.askQuestion(documentId, request))
                .isInstanceOf(DocumentChatNotAvailableException.class)
                .hasMessageContaining("Document is not ready for chat");

        verify(currentUserProvider).getCurrentUserId();
        verify(documentRepository).findByIdAndOwnerId(documentId, ownerId);
        verify(document, never()).getId();
    }
}
