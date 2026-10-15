package com.aidocumentvault.backend.ai.chat.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAiResponsesRequest(
    @JsonProperty("model")
    String model,

    @JsonProperty("input")
    List<OpenAiInputMessage> input,

    @JsonProperty("max_output_tokens")
    int maxOutputTokens
) {

    @Override
    public String toString() {
        return "OpenAiResponsesRequest[model=%s, inputCount=%d, maxOutputTokens=%d]"
            .formatted(
                model,
                input == null ? 0 : input.size(),
                maxOutputTokens
            );
    }
}
