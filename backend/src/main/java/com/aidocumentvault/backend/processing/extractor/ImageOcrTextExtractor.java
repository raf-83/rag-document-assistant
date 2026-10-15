package com.aidocumentvault.backend.processing.extractor;

import com.aidocumentvault.backend.config.properties.OcrProperties;
import com.aidocumentvault.backend.document.entity.ExtractionMethod;
import com.aidocumentvault.backend.processing.dto.TextExtractionResult;
import com.aidocumentvault.backend.processing.exception.TextExtractionException;
import com.aidocumentvault.backend.storage.entity.StoredFile;
import java.nio.file.Path;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Component;

@Component
public class ImageOcrTextExtractor implements TextExtractor {

    private static final String IMAGE_JPEG_MIME_TYPE = "image/jpeg";
    private static final String IMAGE_PNG_MIME_TYPE = "image/png";

    private final OcrProperties ocrProperties;

    public ImageOcrTextExtractor(OcrProperties ocrProperties) {
        this.ocrProperties = ocrProperties;
    }

    @Override
    public boolean supports(StoredFile storedFile) {
        if (storedFile == null || storedFile.getMimeType() == null) {
            return false;
        }

        return IMAGE_JPEG_MIME_TYPE.equalsIgnoreCase(storedFile.getMimeType())
                || IMAGE_PNG_MIME_TYPE.equalsIgnoreCase(storedFile.getMimeType());
    }

    @Override
    public TextExtractionResult extract(Path filePath) {
        try {
            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath(ocrProperties.dataPath());
            tesseract.setLanguage(ocrProperties.language());

            String extractedText = tesseract.doOCR(filePath.toFile());

            return new TextExtractionResult(
                    extractedText,
                    ExtractionMethod.OCR,
                    null
            );
        } catch (TesseractException exception) {
            throw new TextExtractionException("Could not extract text from image file using OCR", exception);
        }
    }
}
