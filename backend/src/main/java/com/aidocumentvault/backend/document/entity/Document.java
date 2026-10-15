package com.aidocumentvault.backend.document.entity;

import com.aidocumentvault.backend.common.entity.BaseEntity;
import com.aidocumentvault.backend.storage.entity.StoredFile;
import com.aidocumentvault.backend.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_type_id", nullable = false)
    private DocumentType documentType;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "stored_file_id", nullable = false, unique = true)
    private StoredFile storedFile;

    @OneToOne(mappedBy = "document", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private DocumentContent content;

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentSummary> summaries = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentStatus status;

    public Document(
        String title,
        User owner,
        DocumentType documentType,
        StoredFile storedFile
    ) {
        changeTitle(title);
        this.owner = Objects.requireNonNull(owner, "owner cannot be null");
        this.documentType = Objects.requireNonNull(documentType, "documentType cannot be null");
        this.storedFile = Objects.requireNonNull(storedFile, "storedFile cannot be null");
        this.status = DocumentStatus.UPLOADED;
    }

    public void changeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be null or blank");
        }

        this.title = title.trim();
    }

    public void markAsUploaded() {
        this.status = DocumentStatus.UPLOADED;
    }

    public void markAsTextProcessing() {
        this.status = DocumentStatus.TEXT_PROCESSING;
    }

    public void markAsContentReady() {
        this.status = DocumentStatus.CONTENT_READY;
    }

    public void markAsChunking() {
        this.status = DocumentStatus.CHUNKING;
    }

    public void markAsReady() {
        this.status = DocumentStatus.READY;
    }

    public void markAsFailed() {
        this.status = DocumentStatus.FAILED;
    }

    public void attachContent(DocumentContent content) {
        if (content == null) {
            throw new IllegalArgumentException("content cannot be null");
        }

        if (this.content != null) {
            this.content.setDocument(null);
        }

        this.content = content;

        if (content.getDocument() != this) {
            content.setDocument(this);
        }
    }

    public void detachContent() {
        if (this.content == null) {
            return;
        }

        DocumentContent oldContent = this.content;
        this.content = null;

        if (oldContent.getDocument() == this) {
            oldContent.setDocument(null);
        }
    }

    public List<DocumentSummary> getSummaries() {
        return Collections.unmodifiableList(summaries);
    }

    public void addSummary(DocumentSummary summary) {
        if (summary == null) {
            throw new IllegalArgumentException("summary cannot be null");
        }

        if (!this.summaries.contains(summary)) {
            this.summaries.add(summary);
        }

        if (summary.getDocument() != this) {
            summary.setDocument(this);
        }
    }

    public void removeSummary(DocumentSummary summary) {
        if (summary == null) {
            return;
        }

        boolean removed = this.summaries.remove(summary);

        if (removed && summary.getDocument() == this) {
            summary.setDocument(null);
        }
    }

    public void clearSummaries() {
        List<DocumentSummary> copy = new ArrayList<>(this.summaries);

        for (DocumentSummary summary : copy) {
            removeSummary(summary);
        }
    }

    public void replaceSummaries(List<DocumentSummary> newSummaries) {
        clearSummaries();

        if (newSummaries == null) {
            return;
        }

        for (DocumentSummary summary : newSummaries) {
            addSummary(summary);
        }
    }
}
