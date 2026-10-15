package com.aidocumentvault.backend.processing;

import com.aidocumentvault.backend.document.entity.Document;
import com.aidocumentvault.backend.document.entity.DocumentContent;
import com.aidocumentvault.backend.document.entity.ExtractionMethod;
import com.aidocumentvault.backend.document.repository.DocumentRepository;
import com.aidocumentvault.backend.processing.chunking.event.DocumentContentReadyEvent;
import com.aidocumentvault.backend.processing.dto.TextExtractionResult;
import com.aidocumentvault.backend.processing.exception.DocumentProcessingException;
import com.aidocumentvault.backend.processing.service.DocumentContentCompletionServiceImpl;
import com.aidocumentvault.backend.processing.service.DocumentContentPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentContentCompletionServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentContentPersistenceService documentContentPersistenceService;

    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private DocumentContentCompletionServiceImpl documentContentCompletionService;

    @Test
    void shouldCompleteContentProcessingSuccessfully() {
        //given
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID documentContentId = UUID.randomUUID();

        String sanitizedText = "jakiś przykładwoy tekst do sanityzacji";

        TextExtractionResult extractionResult = new TextExtractionResult(
            "extractedText",
            ExtractionMethod.PDF_TEXT,
            1);

        Document document = mock(Document.class);
        DocumentContent documentContent = mock(DocumentContent.class);

        when(documentRepository.findByIdAndOwnerId(documentId, userId))
            .thenReturn(Optional.of(document));

        when(documentContentPersistenceService.saveDocumentContent(
            document,
            sanitizedText,
            extractionResult
        )).thenReturn(documentContent);

        when(documentContent.getId()).thenReturn(documentContentId);

        //when
        documentContentCompletionService.completeContentProcessing(documentId, userId, sanitizedText, extractionResult);

        //then
        InOrder inOrder = inOrder(
            documentRepository,
            documentContentPersistenceService,
            document,
            publisher
        );

        inOrder.verify(documentRepository).findByIdAndOwnerId(documentId, userId);

        inOrder.verify(documentContentPersistenceService).saveDocumentContent(
            document,
            sanitizedText,
            extractionResult
        );

        inOrder.verify(document).markAsContentReady();

        inOrder.verify(publisher).publishEvent(any(DocumentContentReadyEvent.class));
    }

    @Test
    void shouldThrowExceptionWhenDocumentDoesNotExist() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String sanitizedText = "jakiś przykładowy tekst";

        TextExtractionResult extractionResult = new TextExtractionResult(
            "extractedText",
            ExtractionMethod.PDF_TEXT,
            1
        );

        when(documentRepository.findByIdAndOwnerId(documentId, userId))
            .thenReturn(Optional.empty());

        // when / then
        assertThrows(IllegalStateException.class, () ->
            documentContentCompletionService.completeContentProcessing(
                documentId,
                userId,
                sanitizedText,
                extractionResult
            )
        );

        verify(documentRepository).findByIdAndOwnerId(documentId, userId);

        verifyNoInteractions(documentContentPersistenceService);
        verifyNoInteractions(publisher);
    }

    @Test
    void shouldThrowExceptionWhenSavingDocumentContentFails() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String sanitizedText = "jakiś przykładowy tekst";

        TextExtractionResult extractionResult = new TextExtractionResult(
            "extractedText",
            ExtractionMethod.PDF_TEXT,
            1
        );

        Document document = mock(Document.class);

        when(documentRepository.findByIdAndOwnerId(documentId, userId))
            .thenReturn(Optional.of(document));

        when(documentContentPersistenceService.saveDocumentContent(
            document,
            sanitizedText,
            extractionResult
        )).thenThrow(new DocumentProcessingException("saving content failed"));

        // when / then
        assertThrows(DocumentProcessingException.class, () ->
            documentContentCompletionService.completeContentProcessing(
                documentId,
                userId,
                sanitizedText,
                extractionResult
            )
        );

        verify(documentRepository).findByIdAndOwnerId(documentId, userId);

        verify(documentContentPersistenceService).saveDocumentContent(
            document,
            sanitizedText,
            extractionResult
        );

        verify(document, never()).markAsContentReady();

        verifyNoInteractions(publisher);
    }
}
