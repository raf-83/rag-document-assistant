package com.aidocumentvault.backend.ai.embedding.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAiEmbeddingResponse(
    @JsonProperty("object")
    String object,

    @JsonProperty("data")
    List<OpenAiEmbeddingData> data,

    @JsonProperty("model")
    String model,

    @JsonProperty("usage")
    OpenAiEmbeddingUsage usage
) {

    @Override
    public String toString() {
        return "OpenAiEmbeddingResponse[object=%s, dataCount=%d, model=%s, usage=%s]"
            .formatted(
                object,
                data == null ? 0 : data.size(),
                model,
                usage
            );
    }
}
