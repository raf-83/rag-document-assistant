package com.aidocumentvault.backend.ai.chat.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiOutputContent(
    @JsonProperty("type")
    String type,

    @JsonProperty("text")
    String text
) {

    @Override
    public String toString() {
        return "OpenAiOutputContent[type=%s, textLength=%d]"
            .formatted(
                type,
                text == null ? 0 : text.length()
            );
    }
}
