package com.aidocumentvault.backend.ai.chat.model;

import java.util.Objects;

public record ChatMessage(
    ChatMessageRole role,
    String content
) {

    public ChatMessage {
        Objects.requireNonNull(role, "Chat message role must not be null");
        Objects.requireNonNull(content, "Chat message content must not be null");

        if (content.isBlank()) {
            throw new IllegalArgumentException(
                "Chat message content must not be blank"
            );
        }
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(ChatMessageRole.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ChatMessageRole.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ChatMessageRole.ASSISTANT, content);
    }

    @Override
    public String toString() {
        return "ChatMessage[role=%s, contentLength=%d]"
            .formatted(role, content.length());
    }
}
