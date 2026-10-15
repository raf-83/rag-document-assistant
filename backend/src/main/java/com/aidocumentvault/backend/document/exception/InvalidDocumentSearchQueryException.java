package com.aidocumentvault.backend.document.exception;

public class InvalidDocumentSearchQueryException extends RuntimeException {

    public InvalidDocumentSearchQueryException(String message) {
        super(message);
    }
}
