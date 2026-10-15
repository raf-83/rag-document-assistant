package com.aidocumentvault.backend.processing.chunking.service;

import com.aidocumentvault.backend.document.entity.DocumentContent;
import com.aidocumentvault.backend.document.repository.DocumentContentRepository;
import com.aidocumentvault.backend.processing.chunking.dto.ChunkCandidate;
import com.aidocumentvault.backend.processing.chunking.strategy.TextChunker;
import com.aidocumentvault.backend.processing.service.DocumentProcessingStatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import com.aidocumentvault.backend.ai.embedding.event.DocumentChunksReadyForEmbeddingEvent;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class) //uruchamia mokito w JUnit 5
class DocumentChunkingServiceTest {

    //Musimy zMockować wszystkie zalezność występujące w serwisie który testujemy
    @Mock
    private DocumentContentRepository documentContentRepository;

    @Mock
    private TextChunker textChunker;

    @Mock
    private DocumentChunkPersistenceService documentChunkPersistenceService;

    @Mock
    private DocumentProcessingStatusService documentProcessingStatusService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private DocumentChunkingServiceImpl documentChunkingService; //Tworzymy instancję testowanego serwisu i wstrzykujemy powyższe Mocki

    @Test
    void shouldChunkDocumentContentSuccessfully() {
        // given
        //tworzymy zmienne z którymi wywoływana jest funkcja którą testujemy
        UUID documentId = UUID.randomUUID();
        UUID documentContentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        //tworzymy wykorzystywane w testowanej funkcji zienne
        String sanitizedText = "To jest przykładowa oczyszczona treść dokumentu do chunkingu.";

        DocumentContent documentContent = mock(DocumentContent.class); //mockujemy documentContent

        List<ChunkCandidate> chunkCandidates = List.of(
            new ChunkCandidate(
                0,
                "To jest przykładowa oczyszczona treść",
                0,
                39,
                39,
                8,
                0,
                10
            ),
            new ChunkCandidate(
                1,
                "treść dokumentu do chunkingu.",
                29,
                59,
                30,
                5,
                10,
                0
            )
        );

        when(documentContentRepository.findByIdAndDocumentIdAndDocumentOwnerId(
            documentContentId,
            documentId,
            userId
        )).thenReturn(Optional.of(documentContent));

        when(documentContent.getSanitizedText()).thenReturn(sanitizedText);

        when(textChunker.chunk(sanitizedText)).thenReturn(chunkCandidates);

        when(documentChunkPersistenceService.replaceChunks(
            documentContent,
            chunkCandidates
        )).thenReturn(2);

        // when
        documentChunkingService.chunkDocumentContent(
            documentId,
            documentContentId,
            userId
        );

        // then
        InOrder inOrder = inOrder(
            documentProcessingStatusService,
            documentContentRepository,
            textChunker,
            documentChunkPersistenceService
        );

        inOrder.verify(documentProcessingStatusService).markAsChunking(documentId);

        inOrder.verify(documentContentRepository).findByIdAndDocumentIdAndDocumentOwnerId(
            documentContentId,
            documentId,
            userId
        );

        inOrder.verify(textChunker).chunk(sanitizedText);

        inOrder.verify(documentChunkPersistenceService).replaceChunks(
            documentContent,
            chunkCandidates
        );

        inOrder.verify(documentProcessingStatusService).markAsReady(documentId);

        verify(applicationEventPublisher).publishEvent(
            new DocumentChunksReadyForEmbeddingEvent(
                documentId,
                documentContentId,
                userId,
                2
            )
        );

        verify(documentProcessingStatusService, never()).markAsFailed(documentId);
    }

    @Test
    void shouldMarkAsFailedWhenDocumentContentDoesNotExist() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID documentContentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(documentContentRepository.findByIdAndDocumentIdAndDocumentOwnerId(
            documentContentId,
            documentId,
            userId
        )).thenReturn(Optional.empty());

        // when
        documentChunkingService.chunkDocumentContent(
            documentId,
            documentContentId,
            userId
        );

        // then
        InOrder inOrder = inOrder(
            documentProcessingStatusService,
            documentContentRepository
        );

        inOrder.verify(documentProcessingStatusService).markAsChunking(documentId);

        inOrder.verify(documentContentRepository).findByIdAndDocumentIdAndDocumentOwnerId(
            documentContentId,
            documentId,
            userId
        );

        inOrder.verify(documentProcessingStatusService).markAsFailed(documentId);

        verify(textChunker, never()).chunk(anyString());

        verify(documentChunkPersistenceService, never()).replaceChunks(any(), any());

        verify(documentProcessingStatusService, never()).markAsReady(documentId);

        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldMarkAsFailedWhenChunkerReturnsEmptyList() {

        // given
        UUID documentId = UUID.randomUUID();
        UUID documentContentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        //tworzymy wykorzystywane w testowanej funkcji zienne
        String sanitizedText = "To jest przykładowa oczyszczona treść dokumentu do chunkingu.";

        List<ChunkCandidate> chunkCandidates = List.of();

        DocumentContent documentContent = mock(DocumentContent.class); //mockujemy documentContent

        when(documentContentRepository.findByIdAndDocumentIdAndDocumentOwnerId(
            documentContentId,
            documentId,
            userId
        )).thenReturn(Optional.of(documentContent));

        when(documentContent.getSanitizedText()).thenReturn(sanitizedText);

        when(textChunker.chunk(sanitizedText)).thenReturn(chunkCandidates);

        //when
        documentChunkingService.chunkDocumentContent(
            documentId,
            documentContentId,
            userId
        );

        //then
        InOrder inOrder = inOrder(
            documentProcessingStatusService,
            documentContentRepository,
            textChunker
        );

        inOrder.verify(documentProcessingStatusService).markAsChunking(documentId);

        inOrder.verify(documentContentRepository).findByIdAndDocumentIdAndDocumentOwnerId(
            documentContentId,
            documentId,
            userId
        );

        inOrder.verify(textChunker).chunk(sanitizedText);

        inOrder.verify(documentProcessingStatusService).markAsFailed(documentId);

        verify(documentChunkPersistenceService, never()).replaceChunks(any(), any());

        verify(documentProcessingStatusService, never()).markAsReady(documentId);

        verify(applicationEventPublisher, never()).publishEvent(any());

    }

    @Test
    void shouldMarkAsFailedWhenChunkerThrowsException() {

        // given
        UUID documentId = UUID.randomUUID();
        UUID documentContentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String sanitizedText = "To jest przykładowa oczyszczona treść dokumentu do chunkingu.";

        DocumentContent documentContent = mock(DocumentContent.class);

        when(documentContentRepository.findByIdAndDocumentIdAndDocumentOwnerId(
            documentContentId,
            documentId,
            userId
        )).thenReturn(Optional.of(documentContent));

        when(documentContent.getSanitizedText()).thenReturn(sanitizedText);

        when(textChunker.chunk(sanitizedText)).thenThrow(new IllegalStateException("Chunking failed"));

        //when
        documentChunkingService.chunkDocumentContent(
            documentId,
            documentContentId,
            userId
        );

        //then
        InOrder inOrder = inOrder(
            documentProcessingStatusService,
            documentContentRepository,
            textChunker
        );

        inOrder.verify(documentProcessingStatusService).markAsChunking(documentId);

        inOrder.verify(documentContentRepository).findByIdAndDocumentIdAndDocumentOwnerId(
            documentContentId,
            documentId,
            userId
        );

        inOrder.verify(textChunker).chunk(sanitizedText);

        inOrder.verify(documentProcessingStatusService).markAsFailed(documentId);

        verify(documentChunkPersistenceService, never()).replaceChunks(any(), any());

        verify(documentProcessingStatusService, never()).markAsReady(documentId);

        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldMarkAsFailedWhenReplacingChunksThrowsException() {
        // given
        //tworzymy zmienne z którymi wywoływana jest funkcja którą testujemy
        UUID documentId = UUID.randomUUID();
        UUID documentContentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        //tworzymy wykorzystywane w testowanej funkcji zienne
        String sanitizedText = "To jest przykładowa oczyszczona treść dokumentu do chunkingu.";

        DocumentContent documentContent = mock(DocumentContent.class); //mockujemy documentContent

        List<ChunkCandidate> chunkCandidates = List.of(
            new ChunkCandidate(
                0,
                "To jest przykładowa oczyszczona treść",
                0,
                39,
                39,
                8,
                0,
                10
            ),
            new ChunkCandidate(
                1,
                "treść dokumentu do chunkingu.",
                29,
                59,
                30,
                5,
                10,
                0
            )
        );

        when(documentContentRepository.findByIdAndDocumentIdAndDocumentOwnerId(
            documentContentId,
            documentId,
            userId
        )).thenReturn(Optional.of(documentContent));

        when(documentContent.getSanitizedText()).thenReturn(sanitizedText);

        when(textChunker.chunk(sanitizedText)).thenReturn(chunkCandidates);

        when(documentChunkPersistenceService.replaceChunks(
            documentContent,
            chunkCandidates
        )).thenThrow(new IllegalStateException("Replace filed"));

        //when
        documentChunkingService.chunkDocumentContent(
            documentId,
            documentContentId,
            userId
        );

        //then
        InOrder inOrder = inOrder(
            documentProcessingStatusService,
            documentContentRepository,
            textChunker,
            documentChunkPersistenceService

        );

        inOrder.verify(documentProcessingStatusService).markAsChunking(documentId);

        inOrder.verify(documentContentRepository).findByIdAndDocumentIdAndDocumentOwnerId(
            documentContentId,
            documentId,
            userId
        );

        inOrder.verify(textChunker).chunk(sanitizedText);

        inOrder.verify(documentChunkPersistenceService).replaceChunks(
            documentContent,
            chunkCandidates
        );

        inOrder.verify(documentProcessingStatusService).markAsFailed(documentId);

        verify(documentProcessingStatusService, never()).markAsReady(documentId);

        verify(applicationEventPublisher, never()).publishEvent(any());
    }
}
