package com.aidocumentvault.backend.ai.chat.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiResponseUsage(
    @JsonProperty("input_tokens")
    Integer inputTokens,

    @JsonProperty("output_tokens")
    Integer outputTokens,

    @JsonProperty("total_tokens")
    Integer totalTokens
) {
}
