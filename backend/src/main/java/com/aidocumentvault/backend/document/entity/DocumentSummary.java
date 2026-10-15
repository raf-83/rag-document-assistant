package com.aidocumentvault.backend.document.entity;

import com.aidocumentvault.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_summaries")
public class DocumentSummary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Enumerated(EnumType.STRING)
    @Column(name = "summary_type", nullable = false, length = 30)
    private SummaryType summaryType;

    @Column(name = "model_name", length = 100)
    private String modelName;

    public Document getDocument() {
        return document;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public SummaryType getSummaryType() {
        return summaryType;
    }

    public String getModelName() {
        return modelName;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public void setSummaryType(SummaryType summaryType) {
        this.summaryType = summaryType;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
}
