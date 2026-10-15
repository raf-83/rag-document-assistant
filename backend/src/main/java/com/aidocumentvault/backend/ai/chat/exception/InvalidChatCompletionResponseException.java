package com.aidocumentvault.backend.ai.chat.exception;

public class InvalidChatCompletionResponseException
    extends ChatCompletionException {

    public InvalidChatCompletionResponseException(String message) {
        super(message);
    }

    public InvalidChatCompletionResponseException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
