package com.aidocumentvault.backend.ai.chat.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiOutputItem(
    @JsonProperty("type")
    String type,

    @JsonProperty("role")
    String role,

    @JsonProperty("content")
    List<OpenAiOutputContent> content
) {

    @Override
    public String toString() {
        return "OpenAiOutputItem[type=%s, role=%s, contentCount=%d]"
            .formatted(
                type,
                role,
                content == null ? 0 : content.size()
            );
    }
}
