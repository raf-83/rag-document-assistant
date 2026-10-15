package com.aidocumentvault.backend.document.entity;

import com.aidocumentvault.backend.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Getter
@Entity
@Table(name = "document_contents")
public class DocumentContent extends BaseEntity {

    private static final String BASIC_SANITIZATION_VERSION = "BASIC_V1";

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    private Document document;

    @Column(name = "sanitized_text", nullable = false, columnDefinition = "TEXT")
    private String sanitizedText;

    @Column(name = "sanitization_version", length = 50)
    private String sanitizationVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_method", nullable = false, length = 50)
    private ExtractionMethod extractionMethod;

    @Column(name = "character_count", nullable = false)
    private Integer characterCount;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "extracted_at", nullable = false)
    private OffsetDateTime extractedAt;

    @Column(name = "sanitized_at", nullable = false)
    private OffsetDateTime sanitizedAt;

    @OneToMany(
        mappedBy = "documentContent",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<DocumentChunk> chunks = new ArrayList<>();

    protected DocumentContent() {
        // wymagane przez JPA
    }

    public DocumentContent(
        Document document,
        String sanitizedText,
        ExtractionMethod extractionMethod,
        Integer pageCount
    ) {
        setDocument(document);
        setSanitizedText(sanitizedText);
        setExtractionMethod(extractionMethod);
        setPageCount(pageCount);

        this.extractedAt = OffsetDateTime.now();
        this.sanitizedAt = OffsetDateTime.now();
        this.sanitizationVersion = BASIC_SANITIZATION_VERSION;
    }

    public DocumentContent(
        Document document,
        String sanitizedText,
        String sanitizationVersion
    ) {
        setDocument(document);
        setSanitizedText(sanitizedText);
        setSanitizationVersion(sanitizationVersion);
    }


    public List<DocumentChunk> getChunks() {
        return Collections.unmodifiableList(chunks);
    }

    public void setDocument(Document document) {
        this.document = Objects.requireNonNull(document, "document cannot be null");
    }

    public void setSanitizedText(String sanitizedText) {
        if (sanitizedText == null || sanitizedText.isBlank()) {
            throw new IllegalArgumentException("sanitizedText cannot be null or blank");
        }

        this.sanitizedText = sanitizedText;
        this.characterCount = sanitizedText.length();
    }

    public void setSanitizationVersion(String sanitizationVersion) {
        this.sanitizationVersion = sanitizationVersion;
    }

    public void setExtractionMethod(ExtractionMethod extractionMethod) {
        this.extractionMethod = Objects.requireNonNull(
            extractionMethod,
            "extractionMethod cannot be null"
        );
    }

    public void setPageCount(Integer pageCount) {
        if (pageCount != null && pageCount < 1) {
            throw new IllegalArgumentException("pageCount cannot be lower than 1");
        }

        this.pageCount = pageCount;
    }

    public void markSanitizedNow() {
        this.sanitizedAt = OffsetDateTime.now();
    }

    public void markExtractedNow() {
        this.extractedAt = OffsetDateTime.now();
    }

    public void addChunk(DocumentChunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk cannot be null");
        }

        if (!this.chunks.contains(chunk)) {
            this.chunks.add(chunk);
        }

        if (chunk.getDocumentContent() != this) {
            chunk.setDocumentContent(this);
        }
    }

    public void removeChunk(DocumentChunk chunk) {
        if (chunk == null) {
            return;
        }

        boolean removed = this.chunks.remove(chunk);

        if (removed && chunk.getDocumentContent() == this) {
            chunk.setDocumentContent(null);
        }
    }

    public void clearChunks() {
        List<DocumentChunk> copy = new ArrayList<>(this.chunks);

        for (DocumentChunk chunk : copy) {
            removeChunk(chunk);
        }
    }

    public void replaceChunks(List<DocumentChunk> newChunks) {
        clearChunks();

        if (newChunks == null) {
            return;
        }

        for (DocumentChunk chunk : newChunks) {
            addChunk(chunk);
        }
    }
}
