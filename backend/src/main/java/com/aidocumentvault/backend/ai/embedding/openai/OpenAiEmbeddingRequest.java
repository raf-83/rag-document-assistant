package com.aidocumentvault.backend.ai.embedding.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAiEmbeddingRequest(
    @JsonProperty("model")
    String model,

    @JsonProperty("input")
    List<String> input,

    @JsonProperty("encoding_format")
    String encodingFormat,

    @JsonProperty("dimensions")
    Integer dimensions

) {
    @Override
    public String toString() {
        return "OpenAiEmbeddingRequest[model=%s, inputCount=%d, encodingFormat=%s, dimensions=%s]"
            .formatted(
                model,
                input == null ? 0 : input.size(),
                encodingFormat,
                dimensions
            );
    }
}
