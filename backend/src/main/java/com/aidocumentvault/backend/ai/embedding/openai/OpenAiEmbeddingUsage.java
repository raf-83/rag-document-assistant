package com.aidocumentvault.backend.ai.embedding.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAiEmbeddingUsage(
    @JsonProperty("prompt_tokens")
    Integer promptTokens,

    @JsonProperty("total_tokens")
    Integer totalTokens
) {
}
