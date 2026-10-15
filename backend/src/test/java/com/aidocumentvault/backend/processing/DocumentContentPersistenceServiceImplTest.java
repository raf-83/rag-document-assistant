package com.aidocumentvault.backend.processing;

import com.aidocumentvault.backend.document.entity.Document;
import com.aidocumentvault.backend.document.entity.DocumentContent;
import com.aidocumentvault.backend.document.entity.ExtractionMethod;
import com.aidocumentvault.backend.document.repository.DocumentContentRepository;
import com.aidocumentvault.backend.processing.dto.TextExtractionResult;
import com.aidocumentvault.backend.processing.exception.DocumentProcessingException;
import com.aidocumentvault.backend.processing.service.DocumentContentPersistenceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentContentPersistenceServiceImplTest {

    @Mock
    private DocumentContentRepository documentContentRepository;

    @InjectMocks
    private DocumentContentPersistenceServiceImpl documentContentPersistenceService;

    @Test
    void shouldSaveDocumentContentSuccessfully() {
        // given
        Document document = mock(Document.class);

        String sanitizedText = "jakiś przykładowy tekst do sanityzacji";

        TextExtractionResult extractionResult = new TextExtractionResult(
            "extractedText",
            ExtractionMethod.PDF_TEXT,
            1
        );

        when(documentContentRepository.save(any(DocumentContent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        DocumentContent savedContent = documentContentPersistenceService.saveDocumentContent(
            document,
            sanitizedText,
            extractionResult
        );

        // then
        ArgumentCaptor<DocumentContent> captor = ArgumentCaptor.forClass(DocumentContent.class);

        verify(documentContentRepository).save(captor.capture());

        DocumentContent capturedContent = captor.getValue();

        assertThat(savedContent).isSameAs(capturedContent);
        assertThat(capturedContent.getDocument()).isSameAs(document);
        assertThat(capturedContent.getSanitizedText()).isEqualTo(sanitizedText);
        assertThat(capturedContent.getExtractionMethod()).isEqualTo(ExtractionMethod.PDF_TEXT);
        assertThat(capturedContent.getPageCount()).isEqualTo(1);
    }

    @Test
    void shouldPropagateExceptionWhenRepositorySaveFails() {
        // given
        Document document = mock(Document.class);

        String sanitizedText = "jakiś przykładowy tekst do sanityzacji";

        TextExtractionResult extractionResult = new TextExtractionResult(
            "extractedText",
            ExtractionMethod.PDF_TEXT,
            1
        );

        DocumentProcessingException exception = new DocumentProcessingException(
            "saving content failed"
        );

        when(documentContentRepository.save(any(DocumentContent.class)))
            .thenThrow(exception);

        // when / then
        DocumentProcessingException thrownException = assertThrows(
            DocumentProcessingException.class,
            () -> documentContentPersistenceService.saveDocumentContent(
                document,
                sanitizedText,
                extractionResult
            )
        );

        assertSame(exception, thrownException);

        verify(documentContentRepository).save(any(DocumentContent.class));
    }
}
