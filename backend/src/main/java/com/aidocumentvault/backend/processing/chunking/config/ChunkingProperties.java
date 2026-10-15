package com.aidocumentvault.backend.processing.chunking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.processing.chunking")
public record ChunkingProperties(
        int maxChunkChars,
        int overlapChars,
        int minChunkChars,
        String strategy
) {
}
