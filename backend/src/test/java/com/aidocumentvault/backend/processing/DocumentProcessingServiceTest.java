package com.aidocumentvault.backend.processing;

import com.aidocumentvault.backend.document.entity.Document;
import com.aidocumentvault.backend.document.entity.ExtractionMethod;
import com.aidocumentvault.backend.document.repository.DocumentRepository;
import com.aidocumentvault.backend.processing.dto.TextExtractionResult;
import com.aidocumentvault.backend.processing.extractor.TextExtractor;
import com.aidocumentvault.backend.processing.sanitizer.TextSanitizer;
import com.aidocumentvault.backend.processing.service.DocumentContentCompletionServiceImpl;
import com.aidocumentvault.backend.processing.service.DocumentProcessingServiceImpl;
import com.aidocumentvault.backend.processing.service.DocumentProcessingStatusService;
import com.aidocumentvault.backend.storage.entity.StoredFile;
import com.aidocumentvault.backend.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class) //uruchamia mokito w JUnit 5
public class DocumentProcessingServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentProcessingStatusService statusService;

    @Mock
    private StorageService storageService;

    @Mock
    private TextExtractor textExtractor;

    @Mock
    private TextSanitizer textSanitizer;

    @Mock
    private DocumentContentCompletionServiceImpl contentCompletionService;

    private DocumentProcessingServiceImpl documentProcessingService;

    @BeforeEach
    void setUp() {
        documentProcessingService = new DocumentProcessingServiceImpl(
            documentRepository,
            storageService,
            List.of(textExtractor),
            textSanitizer,
            statusService,
            contentCompletionService
        );
    }

    @Test
    void shouldProcessDocumentSuccessfully() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID userId =  UUID.randomUUID();

        Document document = mock(Document.class);

        StoredFile storedFile = mock(StoredFile.class);

        Path filePath = Path.of("test-file.pdf");

        TextExtractionResult extractionResult = new TextExtractionResult(
            "extractedText",
            ExtractionMethod.PDF_TEXT,
            1);

        String sanitizedText = "To jest przykładowa oczyszczona treść dokumentu do chunkingu.";

        when(documentRepository.findWithStoredFileByIdAndOwnerId(documentId, userId))
            .thenReturn(Optional.of(document));

        when(document.getStoredFile()).thenReturn(storedFile);

        when(textExtractor.supports(storedFile)).thenReturn(true);

        when(storedFile.getStorageKey()).thenReturn("documents/test-file.pdf");

        when(storageService.resolvePathForProcessing("documents/test-file.pdf"))
            .thenReturn(filePath);

        when(textExtractor.extract(filePath))
            .thenReturn(extractionResult);

        when(textSanitizer.sanitize("extractedText"))
            .thenReturn(sanitizedText);


        //when
        documentProcessingService.processDocument(documentId, userId);


        //then
        InOrder inOrder = inOrder(
            statusService,
            documentRepository,
            textExtractor,
            storageService,
            textSanitizer,
            contentCompletionService
        );

        inOrder.verify(documentRepository).findWithStoredFileByIdAndOwnerId(documentId, userId);

        inOrder.verify(statusService).markAsTextProcessing(documentId);

        inOrder.verify(textExtractor).supports(storedFile);

        inOrder.verify(storageService).resolvePathForProcessing("documents/test-file.pdf");

        inOrder.verify(textExtractor).extract(filePath);

        inOrder.verify(textSanitizer).sanitize("extractedText");

        inOrder.verify(contentCompletionService).completeContentProcessing(documentId, userId, sanitizedText, extractionResult);

        verify(statusService, never()).markAsFailed(documentId);
    }



    @Test
    void shouldMarkAsFailedWhenDocumentDoesNotExist() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(documentRepository.findWithStoredFileByIdAndOwnerId(documentId, userId))
            .thenReturn(Optional.empty());

        // when
        documentProcessingService.processDocument(documentId, userId);

        // then
        InOrder inOrder = inOrder(
            documentRepository,
            statusService
        );

        inOrder.verify(documentRepository).findWithStoredFileByIdAndOwnerId(documentId, userId);
        inOrder.verify(statusService).markAsFailed(documentId);

        verify(statusService, never()).markAsTextProcessing(documentId);

        verifyNoInteractions(textExtractor);
        verifyNoInteractions(storageService);
        verifyNoInteractions(textSanitizer);
        verifyNoInteractions(contentCompletionService);
    }

    @Test
    void shouldMarkAsFailedWhenNoExtractorSupportsStoredFile(){

        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Document document = mock(Document.class);
        StoredFile storedFile = mock(StoredFile.class);

        when(documentRepository.findWithStoredFileByIdAndOwnerId(documentId, userId))
            .thenReturn(Optional.of(document));

        when(document.getStoredFile()).thenReturn(storedFile);

        when(textExtractor.supports(storedFile)).thenReturn(false);

        // when
        documentProcessingService.processDocument(documentId, userId);

        // then
        InOrder inOrder = inOrder(
            documentRepository,
            statusService,
            textExtractor
        );

        inOrder.verify(documentRepository).findWithStoredFileByIdAndOwnerId(documentId, userId);
        inOrder.verify(statusService).markAsTextProcessing(documentId);
        inOrder.verify(textExtractor).supports(storedFile);
        inOrder.verify(statusService).markAsFailed(documentId);

        verify(textExtractor, never()).extract(any(Path.class));

        verifyNoInteractions(storageService);
        verifyNoInteractions(textSanitizer);
        verifyNoInteractions(contentCompletionService);

    }



    @Test
    void shouldMarkAsFailedWhenStorageFileCannotBeResolved(){

        // given
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Document document = mock(Document.class);

        StoredFile storedFile = mock(StoredFile.class);

        when(documentRepository.findWithStoredFileByIdAndOwnerId(documentId, userId))
            .thenReturn(Optional.of(document));

        when(document.getStoredFile()).thenReturn(storedFile);

        when(textExtractor.supports(storedFile)).thenReturn(true);

        when(storedFile.getStorageKey()).thenReturn("documents/test-file.pdf");

        when(storageService.resolvePathForProcessing("documents/test-file.pdf")).thenThrow(new IllegalStateException("resolve failed"));

        // when
        documentProcessingService.processDocument(documentId, userId);

        // then
        InOrder inOrder = inOrder(
            documentRepository,
            statusService,
            textExtractor,
            storageService
        );

        inOrder.verify(documentRepository).findWithStoredFileByIdAndOwnerId(documentId, userId);
        inOrder.verify(statusService).markAsTextProcessing(documentId);
        inOrder.verify(textExtractor).supports(storedFile);
        inOrder.verify(storageService).resolvePathForProcessing("documents/test-file.pdf");
        inOrder.verify(statusService).markAsFailed(documentId);

        verify(textExtractor, never()).extract(any(Path.class));

        verifyNoInteractions(textSanitizer);
        verifyNoInteractions(contentCompletionService);

    }

    @Test
    void shouldMarkAsFailedWhenExtractorThrowsException(){
        // given
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Path filePath = Path.of("test-file.pdf");

        Document document = mock(Document.class);

        StoredFile storedFile = mock(StoredFile.class);

        when(documentRepository.findWithStoredFileByIdAndOwnerId(documentId, userId))
            .thenReturn(Optional.of(document));

        when(document.getStoredFile()).thenReturn(storedFile);

        when(textExtractor.supports(storedFile)).thenReturn(true);

        when(storedFile.getStorageKey()).thenReturn("documents/test-file.pdf");

        when(storageService.resolvePathForProcessing("documents/test-file.pdf")).thenReturn(filePath);

        when(textExtractor.extract(filePath)).thenThrow(new IllegalStateException("extract failed"));

        // when
        documentProcessingService.processDocument(documentId, userId);

        // then
        InOrder inOrder = inOrder(
            documentRepository,
            statusService,
            textExtractor,
            storageService
        );

        inOrder.verify(documentRepository).findWithStoredFileByIdAndOwnerId(documentId, userId);
        inOrder.verify(statusService).markAsTextProcessing(documentId);
        inOrder.verify(textExtractor).supports(storedFile);
        inOrder.verify(storageService).resolvePathForProcessing("documents/test-file.pdf");
        inOrder.verify(textExtractor).extract(filePath);
        inOrder.verify(statusService).markAsFailed(documentId);


        verifyNoInteractions(textSanitizer);
        verifyNoInteractions(contentCompletionService);

    }


    @Test
    void shouldMarkAsFailedWhenSanitizerThrowsException(){
        // given
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Path filePath = Path.of("test-file.pdf");

        Document document = mock(Document.class);

        StoredFile storedFile = mock(StoredFile.class);

        TextExtractionResult extractionResult = new TextExtractionResult(
            "extractedText",
            ExtractionMethod.PDF_TEXT,
            1);

        when(documentRepository.findWithStoredFileByIdAndOwnerId(documentId, userId))
            .thenReturn(Optional.of(document));

        when(document.getStoredFile()).thenReturn(storedFile);

        when(textExtractor.supports(storedFile)).thenReturn(true);

        when(storedFile.getStorageKey()).thenReturn("documents/test-file.pdf");

        when(storageService.resolvePathForProcessing("documents/test-file.pdf")).thenReturn(filePath);

        when(textExtractor.extract(filePath)).thenReturn(extractionResult);

        when(textSanitizer.sanitize("extractedText")).thenThrow(new IllegalStateException("sanitize failed"));

        // when
        documentProcessingService.processDocument(documentId, userId);

        // then
        InOrder inOrder = inOrder(
            documentRepository,
            statusService,
            textExtractor,
            storageService,
            textSanitizer
        );

        inOrder.verify(documentRepository).findWithStoredFileByIdAndOwnerId(documentId, userId);
        inOrder.verify(statusService).markAsTextProcessing(documentId);
        inOrder.verify(textExtractor).supports(storedFile);
        inOrder.verify(storageService).resolvePathForProcessing("documents/test-file.pdf");
        inOrder.verify(textExtractor).extract(filePath);
        inOrder.verify(textSanitizer).sanitize("extractedText");
        inOrder.verify(statusService).markAsFailed(documentId);

        verifyNoInteractions(contentCompletionService);

    }

    @Test
    void shouldMarkAsFailedWhenContentCompletionThrowsException() {

        // given
        UUID documentId = UUID.randomUUID();
        UUID userId =  UUID.randomUUID();

        Document document = mock(Document.class);

        StoredFile storedFile = mock(StoredFile.class);

        Path filePath = Path.of("test-file.pdf");

        TextExtractionResult extractionResult = new TextExtractionResult(
            "extractedText",
            ExtractionMethod.PDF_TEXT,
            1);

        String sanitizedText = "To jest przykładowa oczyszczona treść dokumentu do chunkingu.";

        when(documentRepository.findWithStoredFileByIdAndOwnerId(documentId, userId))
            .thenReturn(Optional.of(document));

        when(document.getStoredFile()).thenReturn(storedFile);

        when(textExtractor.supports(storedFile)).thenReturn(true);

        when(storedFile.getStorageKey()).thenReturn("documents/test-file.pdf");

        when(storageService.resolvePathForProcessing("documents/test-file.pdf"))
            .thenReturn(filePath);

        when(textExtractor.extract(filePath))
            .thenReturn(extractionResult);

        when(textSanitizer.sanitize("extractedText"))
            .thenReturn(sanitizedText);

        doThrow(new IllegalStateException("completion failed"))
            .when(contentCompletionService)
            .completeContentProcessing(
                documentId,
                userId,
                sanitizedText,
                extractionResult
            );

        //when
        documentProcessingService.processDocument(documentId, userId);


        //then
        InOrder inOrder = inOrder(
            statusService,
            documentRepository,
            textExtractor,
            storageService,
            textSanitizer,
            contentCompletionService
        );

        inOrder.verify(documentRepository).findWithStoredFileByIdAndOwnerId(documentId, userId);

        inOrder.verify(statusService).markAsTextProcessing(documentId);

        inOrder.verify(textExtractor).supports(storedFile);

        inOrder.verify(storageService).resolvePathForProcessing("documents/test-file.pdf");

        inOrder.verify(textExtractor).extract(filePath);

        inOrder.verify(textSanitizer).sanitize("extractedText");

        inOrder.verify(contentCompletionService).completeContentProcessing(documentId, userId, sanitizedText, extractionResult);

        inOrder.verify(statusService).markAsFailed(documentId);

    }
}
