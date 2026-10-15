package com.aidocumentvault.backend.ai.chat.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiResponsesResponse(
    @JsonProperty("id")
    String id,

    @JsonProperty("status")
    String status,

    @JsonProperty("model")
    String model,

    @JsonProperty("output")
    List<OpenAiOutputItem> output,

    @JsonProperty("usage")
    OpenAiResponseUsage usage
) {

    @Override
    public String toString() {
        return "OpenAiResponsesResponse[id=%s, status=%s, model=%s, outputCount=%d, usage=%s]"
            .formatted(
                id,
                status,
                model,
                output == null ? 0 : output.size(),
                usage
            );
    }
}
