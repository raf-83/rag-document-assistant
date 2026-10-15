package com.aidocumentvault.backend.ai.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.ai.chat-completion")
public record AiChatCompletionProperties(
    @DefaultValue("gpt-4o-mini")
    String model,

    @DefaultValue("800")
    int maxOutputTokens
) {

    private static final int MIN_OUTPUT_TOKENS = 1;
    private static final int MAX_OUTPUT_TOKENS = 4096;

    public AiChatCompletionProperties {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException(
                "Chat completion model must not be blank"
            );
        }

        if (
            maxOutputTokens < MIN_OUTPUT_TOKENS
                || maxOutputTokens > MAX_OUTPUT_TOKENS
        ) {
            throw new IllegalArgumentException(
                "Chat completion maxOutputTokens must be between "
                    + MIN_OUTPUT_TOKENS
                    + " and "
                    + MAX_OUTPUT_TOKENS
            );
        }

        model = model.trim();
    }
}
