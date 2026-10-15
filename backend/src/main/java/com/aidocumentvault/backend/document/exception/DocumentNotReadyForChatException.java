package com.aidocumentvault.backend.document.exception;



import com.aidocumentvault.backend.document.entity.DocumentStatus;

import java.util.UUID;

public class DocumentNotReadyForChatException extends RuntimeException {

    private final UUID documentId;
    private final DocumentStatus currentStatus;

    public DocumentNotReadyForChatException(UUID documentId, DocumentStatus currentStatus) {
        super("Document is not ready for chat. Current status: " + currentStatus);
        this.documentId = documentId;
        this.currentStatus = currentStatus;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public DocumentStatus getCurrentStatus() {
        return currentStatus;
    }
}
