package com.aidocumentvault.backend.processing.extractor;

import com.aidocumentvault.backend.document.entity.ExtractionMethod;
import com.aidocumentvault.backend.processing.dto.TextExtractionResult;
import com.aidocumentvault.backend.processing.exception.TextExtractionException;
import com.aidocumentvault.backend.storage.entity.StoredFile;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor implements TextExtractor {

    private static final String PDF_MIME_TYPE = "application/pdf";

    @Override
    public boolean supports(StoredFile storedFile) {
        return storedFile != null
                && PDF_MIME_TYPE.equalsIgnoreCase(storedFile.getMimeType());
    }

    @Override
    public TextExtractionResult extract(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();

            String extractedText = stripper.getText(document);
            int pageCount = document.getNumberOfPages();

            return new TextExtractionResult(
                    extractedText,
                    ExtractionMethod.PDF_TEXT,
                    pageCount
            );
        } catch (IOException exception) {
            throw new TextExtractionException("Could not extract text from PDF file", exception);
        }
    }
}
