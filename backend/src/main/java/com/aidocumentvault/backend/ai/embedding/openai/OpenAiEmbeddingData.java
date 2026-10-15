package com.aidocumentvault.backend.ai.embedding.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAiEmbeddingData(
    @JsonProperty("object")
    String object,

    @JsonProperty("index")
    Integer index,

    @JsonProperty("embedding")
    List<Double> embedding
) {
    @Override
    public String toString() {
        return "OpenAiEmbeddingData[index=%s, embeddingDimensions=%d]"
            .formatted(
                index,
                embedding == null ? 0 : embedding.size()
            );
    }

}
