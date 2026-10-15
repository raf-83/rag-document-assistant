package com.aidocumentvault.backend.ai.chat.model;

import java.util.Objects;

public record ChatCompletionResult(
    String content,
    String model,
    ChatTokenUsage tokenUsage
) {

    public ChatCompletionResult {
        Objects.requireNonNull(
            content,
            "Chat completion content must not be null"
        );

        Objects.requireNonNull(
            model,
            "Chat completion model must not be null"
        );

        Objects.requireNonNull(
            tokenUsage,
            "Chat completion token usage must not be null"
        );

        if (content.isBlank()) {
            throw new IllegalArgumentException(
                "Chat completion content must not be blank"
            );
        }

        if (model.isBlank()) {
            throw new IllegalArgumentException(
                "Chat completion model must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "ChatCompletionResult[contentLength=%d, model=%s, tokenUsage=%s]"
            .formatted(
                content.length(),
                model,
                tokenUsage
            );
    }
}
