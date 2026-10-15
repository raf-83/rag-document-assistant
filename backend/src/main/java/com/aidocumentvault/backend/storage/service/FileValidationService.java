package com.aidocumentvault.backend.storage.service;

import com.aidocumentvault.backend.config.properties.StorageProperties;
import com.aidocumentvault.backend.storage.exception.InvalidFileException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileValidationService {

    private final StorageProperties storageProperties;
    private final PdfValidationService pdfValidationService;

    private final Tika tika = new Tika();

    public void validate(MultipartFile file) {
        validateFilePresent(file);
        validateSize(file);

        String extension = extractExtension(file);

        validateExtension(extension);
        validateDeclaredMimeType(file);

        String detectedMimeType = detectMimeType(file);

        validateDetectedMimeType(detectedMimeType, extension);
        validatePdfIfNeeded(file, detectedMimeType);
    }

    private void validateFilePresent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file is missing or empty");
        }
    }

    private void validateSize(MultipartFile file) {
        Long maxFileSizeBytes = storageProperties.maxFileSizeBytes();

        if (maxFileSizeBytes == null || maxFileSizeBytes <= 0) {
            throw new InvalidFileException("Storage max file size is not configured properly");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidFileException("Uploaded file is too large");
        }
    }

    private String extractExtension(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null || originalFileName.isBlank()) {
            throw new InvalidFileException("Original file name is missing");
        }

        String safeFileName = Path.of(originalFileName).getFileName().toString();
        int dotIndex = safeFileName.lastIndexOf('.');

        if (dotIndex < 0 || dotIndex == safeFileName.length() - 1) {
            throw new InvalidFileException("File extension is missing");
        }

        return safeFileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String extension) {
        boolean allowed = storageProperties.allowedExtensions()
            .stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(value -> value.equals(extension));

        if (!allowed) {
            throw new InvalidFileException("File extension is not allowed");
        }
    }

    private void validateDeclaredMimeType(MultipartFile file) {
        String declaredMimeType = file.getContentType();

        if (declaredMimeType == null || declaredMimeType.isBlank()) {
            throw new InvalidFileException("File MIME type is missing");
        }

        boolean allowed = storageProperties.allowedMimeTypes()
            .stream()
            .anyMatch(value -> value.equalsIgnoreCase(declaredMimeType));

        if (!allowed) {
            throw new InvalidFileException("Declared file MIME type is not allowed");
        }
    }

    private String validateDetectedMimeType(String detectedMimeType , String extension) {

        boolean allowed = storageProperties.allowedMimeTypes()
            .stream()
            .anyMatch(value -> value.equalsIgnoreCase(detectedMimeType));

        if (!allowed) {
            throw new InvalidFileException("Detected file MIME type is not allowed");
        }

        validateExtensionMatchesDetectedMimeType(extension, detectedMimeType);

        return detectedMimeType;
    }

    private String detectMimeType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        } catch (IOException exception) {
            throw new InvalidFileException("Could not detect file MIME type");
        }
    }

    private void validateExtensionMatchesDetectedMimeType(String extension, String detectedMimeType) {
        boolean matches = switch (extension) {
            case "pdf" -> detectedMimeType.equalsIgnoreCase("application/pdf");
            case "jpg", "jpeg" -> detectedMimeType.equalsIgnoreCase("image/jpeg");
            case "png" -> detectedMimeType.equalsIgnoreCase("image/png");
            default -> false;
        };

        if (!matches) {
            throw new InvalidFileException("File extension does not match detected MIME type");
        }
    }

    private void validatePdfIfNeeded(MultipartFile file, String detectedMimeType) {
        if ("application/pdf".equalsIgnoreCase(detectedMimeType)) {
            pdfValidationService.validate(file);
        }
    }
}
