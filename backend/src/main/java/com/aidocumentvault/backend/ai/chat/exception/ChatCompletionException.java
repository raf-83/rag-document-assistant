package com.aidocumentvault.backend.ai.chat.exception;

public class ChatCompletionException extends RuntimeException {

    public ChatCompletionException(String message) {
        super(message);
    }

    public ChatCompletionException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
