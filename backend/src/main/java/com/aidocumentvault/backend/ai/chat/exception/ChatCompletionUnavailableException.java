package com.aidocumentvault.backend.ai.chat.exception;

public class ChatCompletionUnavailableException
    extends ChatCompletionException {

    public ChatCompletionUnavailableException(String message) {
        super(message);
    }

    public ChatCompletionUnavailableException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
