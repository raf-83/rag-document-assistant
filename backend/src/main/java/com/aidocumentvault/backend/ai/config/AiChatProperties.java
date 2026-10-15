package com.aidocumentvault.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.ai.chat")
public record AiChatProperties(
        @DefaultValue("5")
        int maxRetrievedChunks,

        @DefaultValue("8000")
        int maxContextChars
) {

    public AiChatProperties {
        if (maxRetrievedChunks < 1 || maxRetrievedChunks > 20) {
            throw new IllegalArgumentException(
                    "maxRetrievedChunks must be between 1 and 20"
            );
        }

        if (maxContextChars < 1 || maxContextChars > 50000) {
            throw new IllegalArgumentException(
                    "maxContextChars must be between 1 and 50000"
            );
        }
    }
}
