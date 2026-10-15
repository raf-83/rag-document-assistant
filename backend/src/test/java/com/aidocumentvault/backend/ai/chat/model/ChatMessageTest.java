package com.aidocumentvault.backend.ai.chat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChatMessageTest {

    @Test
    void shouldCreateMessagesUsingRoleFactories() {
        // when
        ChatMessage systemMessage = ChatMessage.system("System instruction");
        ChatMessage userMessage = ChatMessage.user("User question");
        ChatMessage assistantMessage = ChatMessage.assistant("Assistant answer");

        // then
        assertThat(systemMessage.role()).isEqualTo(ChatMessageRole.SYSTEM);
        assertThat(systemMessage.content()).isEqualTo("System instruction");
        assertThat(userMessage.role()).isEqualTo(ChatMessageRole.USER);
        assertThat(userMessage.content()).isEqualTo("User question");
        assertThat(assistantMessage.role()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(assistantMessage.content()).isEqualTo("Assistant answer");
    }

    @Test
    void shouldRejectNullRole() {
        assertThatThrownBy(() -> new ChatMessage(null, "Content"))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Chat message role must not be null");
    }

    @Test
    void shouldRejectNullContent() {
        assertThatThrownBy(() -> ChatMessage.user(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Chat message content must not be null");
    }

    @Test
    void shouldRejectBlankContent() {
        assertThatThrownBy(() -> ChatMessage.user(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Chat message content must not be blank");
    }

    @Test
    void shouldPreserveContentFormatting() {
        ChatMessage message = ChatMessage.user("  Pytanie\nŹródło  ");

        assertThat(message.content()).isEqualTo("  Pytanie\nŹródło  ");
    }

    @Test
    void shouldNotExposeMessageContentInToString() {
        ChatMessage message = ChatMessage.user("Poufne pytanie użytkownika");

        String value = message.toString();

        assertThat(value)
            .contains("role=USER")
            .contains("contentLength=26")
            .doesNotContain("Poufne pytanie użytkownika");
    }
}
