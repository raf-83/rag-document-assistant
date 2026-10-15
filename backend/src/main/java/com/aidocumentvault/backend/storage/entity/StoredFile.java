package com.aidocumentvault.backend.storage.entity;

import com.aidocumentvault.backend.common.entity.BaseEntity;
import com.aidocumentvault.backend.document.entity.Document;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "stored_files")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile extends BaseEntity {

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(name = "stored_file_name", length = 255)
    private String storedFileName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_extension", length = 20)
    private String fileExtension;

    @Column(name = "size_in_bytes", nullable = false)
    private Long sizeInBytes;

    @Column(length = 128)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 30)
    private StorageProvider storageProvider;

    @OneToOne(mappedBy = "storedFile", fetch = FetchType.LAZY)
    private Document document;

    public StoredFile(
        String originalFileName,
        String storageKey,
        String storedFileName,
        String mimeType,
        String fileExtension,
        Long sizeInBytes,
        String checksum,
        StorageProvider storageProvider
    ) {
        this.originalFileName = requireNotBlank(originalFileName, "originalFileName");
        this.storageKey = requireNotBlank(storageKey, "storageKey");
        this.storedFileName = normalizeOptional(storedFileName);
        this.mimeType = requireNotBlank(mimeType, "mimeType");
        this.fileExtension = normalizeOptional(fileExtension);
        this.sizeInBytes = requirePositive(sizeInBytes, "sizeInBytes");
        this.checksum = normalizeOptional(checksum);
        this.storageProvider = Objects.requireNonNull(storageProvider, "storageProvider cannot be null");
    }

    public void attachDocument(Document document) {
        this.document = Objects.requireNonNull(document, "document cannot be null");
    }

    public void detachDocument() {
        this.document = null;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }

        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0");
        }

        return value;
    }
}
