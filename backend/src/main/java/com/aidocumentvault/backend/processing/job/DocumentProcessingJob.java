package com.aidocumentvault.backend.processing.job;

import com.aidocumentvault.backend.common.entity.BaseEntity;
import com.aidocumentvault.backend.document.entity.Document;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "document_processing_jobs")
public class DocumentProcessingJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private Document document;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DocumentProcessingJobStatus status;

    public DocumentProcessingJob(Document document, UUID correlationId) {
        this.document = Objects.requireNonNull(document, "document must not be null");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        this.status = DocumentProcessingJobStatus.PENDING;
    }

    public void start() {
        transition(DocumentProcessingJobStatus.PENDING, DocumentProcessingJobStatus.RUNNING);
    }

    public void succeed() {
        transition(DocumentProcessingJobStatus.RUNNING, DocumentProcessingJobStatus.SUCCEEDED);
    }

    public void fail() {
        transition(DocumentProcessingJobStatus.RUNNING, DocumentProcessingJobStatus.FAILED);
    }

    private void transition(
        DocumentProcessingJobStatus expected,
        DocumentProcessingJobStatus next
    ) {
        if (status != expected) {
            throw new IllegalStateException(
                "Cannot change processing job status from " + status + " to " + next
            );
        }
        status = next;
    }
}
