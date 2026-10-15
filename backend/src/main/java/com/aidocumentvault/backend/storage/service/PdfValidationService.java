package com.aidocumentvault.backend.storage.service;

import com.aidocumentvault.backend.config.properties.StorageProperties;
import com.aidocumentvault.backend.storage.exception.InvalidFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PdfValidationService {

    private final StorageProperties storageProperties;

    public void validate(MultipartFile file) {
        Path tempFile = null;

        try {
            tempFile = Files.createTempFile("upload-", ".pdf");
            file.transferTo(tempFile);

            try (PDDocument document = Loader.loadPDF(tempFile.toFile())) {
                validatePageCount(document);
                validateNoActiveContent(document);
            }

        } catch (IOException exception) {
            throw new InvalidFileException("Could not validate PDF file");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException exception) {

                }
            }
        }
    }

    private void validatePageCount(PDDocument document) {
        Integer maxPdfPages = storageProperties.maxPdfPages();

        if (maxPdfPages == null || maxPdfPages <= 0) {
            throw new InvalidFileException("Max PDF pages is not configured properly");
        }

        int pageCount = document.getNumberOfPages();

        if (pageCount > maxPdfPages) {
            throw new InvalidFileException("PDF has too many pages");
        }
    }

    private void validateNoActiveContent(PDDocument document) {
        COSDictionary catalogDictionary = document.getDocumentCatalog().getCOSObject();

        if (containsForbiddenPdfEntry(catalogDictionary)) {
            throw new InvalidFileException("PDF contains active or potentially unsafe content");
        }
    }

    private static final COSName COS_NAME_LAUNCH = COSName.getPDFName("Launch");

    private boolean containsForbiddenPdfEntry(COSDictionary dictionary) {
        return dictionary.containsKey(COSName.OPEN_ACTION)
            || dictionary.containsKey(COSName.AA)
            || dictionary.containsKey(COSName.JS)
            || dictionary.containsKey(COSName.JAVA_SCRIPT)
            || dictionary.containsKey(COSName.EMBEDDED_FILES)
            || COS_NAME_LAUNCH.equals(dictionary.getDictionaryObject(COSName.S));
    }
}
