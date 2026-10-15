package com.aidocumentvault.backend.ai.config;

import com.aidocumentvault.backend.document.entity.DocumentChunkEmbedding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.ai.embedding")
public record AiEmbeddingProperties(
    @DefaultValue("text-embedding-3-small")
    String model,

    @DefaultValue("1536")
    int dimensions,

    @DefaultValue("32")
    int batchSize
) {

    public AiEmbeddingProperties {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("embedding model must not be blank");
        }

        if (dimensions != DocumentChunkEmbedding.SUPPORTED_DIMENSIONS) {
            throw new IllegalArgumentException(
                "embedding dimensions must be "
                    + DocumentChunkEmbedding.SUPPORTED_DIMENSIONS
                    + " for current database schema"
            );
        }

        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException(
                "embedding batchSize must be between 1 and 100"
            );
        }

        model = model.trim();
    }
}
