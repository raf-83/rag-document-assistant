package com.aidocumentvault.backend.ai.chat.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAiInputContent(
    @JsonProperty("type")
    String type,

    @JsonProperty("text")
    String text
) {

    @Override
    public String toString() {
        return "OpenAiInputContent[type=%s, textLength=%d]"
            .formatted(
                type,
                text == null ? 0 : text.length()
            );
    }
}
