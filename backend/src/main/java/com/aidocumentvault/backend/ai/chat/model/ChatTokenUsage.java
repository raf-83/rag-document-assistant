package com.aidocumentvault.backend.ai.chat.model;

public record ChatTokenUsage(
    int inputTokens,
    int outputTokens
) {

    public ChatTokenUsage {
        if (inputTokens < 0) {
            throw new IllegalArgumentException(
                "Input token count must not be negative"
            );
        }

        if (outputTokens < 0) {
            throw new IllegalArgumentException(
                "Output token count must not be negative"
            );
        }
    }

    public int totalTokens() {
        return Math.addExact(inputTokens, outputTokens);
    }
}
