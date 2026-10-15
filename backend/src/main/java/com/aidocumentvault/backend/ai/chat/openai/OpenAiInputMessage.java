package com.aidocumentvault.backend.ai.chat.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAiInputMessage(
    @JsonProperty("role")
    String role,

    @JsonProperty("content")
    List<OpenAiInputContent> content
) {

    @Override
    public String toString() {
        return "OpenAiInputMessage[role=%s, contentCount=%d]"
            .formatted(
                role,
                content == null ? 0 : content.size()
            );
    }
}
