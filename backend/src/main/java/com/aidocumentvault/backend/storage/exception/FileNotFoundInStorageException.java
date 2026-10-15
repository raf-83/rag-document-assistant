package com.aidocumentvault.backend.storage.exception;

public class FileNotFoundInStorageException extends RuntimeException {

    public FileNotFoundInStorageException(String message) {
        super(message);
    }
}
