package com.aidocumentvault.backend.storage.service;

import com.aidocumentvault.backend.config.properties.StorageProperties;
import com.aidocumentvault.backend.storage.dto.StoredFileDownload;
import com.aidocumentvault.backend.storage.dto.StoredFileStorageResult;
import com.aidocumentvault.backend.storage.entity.StorageProvider;
import com.aidocumentvault.backend.storage.exception.FileNotFoundInStorageException;
import com.aidocumentvault.backend.storage.exception.FileStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private static final String DOCUMENTS_DIRECTORY = "documents";

    private final StorageProperties storageProperties;

    private final FileValidationService fileValidationService;

    @Override
    public StoredFileStorageResult store(MultipartFile file) {
        fileValidationService.validate(file);

        String originalFileName = requireOriginalFileName(file);
        String fileExtension = extractExtension(originalFileName);
        String storedFileName = UUID.randomUUID() + "." + fileExtension;
        String storageKey = DOCUMENTS_DIRECTORY + "/" + storedFileName;

        Path targetPath = resolveTargetPath(storageKey);

        try {
            Files.createDirectories(targetPath.getParent());

            String checksum = copyFileAndCalculateChecksum(file, targetPath);

            return new StoredFileStorageResult(
                    originalFileName,
                    storageKey,
                    storedFileName,
                    file.getContentType(),
                    fileExtension,
                    file.getSize(),
                    checksum,
                    StorageProvider.LOCAL_PRIVATE
            );
        } catch (IOException exception) {
            throw new FileStorageException("Could not store uploaded file", exception);
        }
    }

    @Override
    public StoredFileDownload load(
        String storageKey,
        String originalFileName,
        String mimeType,
        Long sizeInBytes
    ) {
        Path filePath = resolveTargetPath(storageKey);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new FileNotFoundInStorageException("File not found in storage");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new FileNotFoundInStorageException("File is not readable from storage");
            }

            return new StoredFileDownload(
                resource,
                originalFileName,
                mimeType,
                sizeInBytes
            );
        } catch (MalformedURLException exception) {
            throw new FileStorageException("Could not load file from storage", exception);
        }
    }

    @Override
    public Path resolvePathForProcessing(String storageKey) {
        return resolveTargetPath(storageKey);
    }

    private String requireOriginalFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null || originalFileName.isBlank()) {
            throw new FileStorageException("Original file name is missing");
        }

        return Path.of(originalFileName).getFileName().toString();
    }

    private String extractExtension(String originalFileName) {
        int dotIndex = originalFileName.lastIndexOf('.');

        if (dotIndex < 0 || dotIndex == originalFileName.length() - 1) {
            throw new FileStorageException("File extension is missing");
        }

        return originalFileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private Path resolveTargetPath(String storageKey) {
        Path basePath = Path.of(storageProperties.local().basePath()).toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(storageKey).normalize();

        if (!targetPath.startsWith(basePath)) {
            throw new FileStorageException("Invalid storage path");
        }

        return targetPath;
    }

    private String copyFileAndCalculateChecksum(MultipartFile file, Path targetPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (
                    InputStream inputStream = file.getInputStream();
                    DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)
            ) {
                Files.copy(digestInputStream, targetPath);
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new FileStorageException("Could not copy uploaded file", exception);
        }
    }


}
