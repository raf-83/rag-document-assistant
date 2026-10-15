package com.aidocumentvault.backend.ai.chat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChatCompletionResultTest {

    @Test
    void shouldCreateValidCompletionResult() {
        ChatTokenUsage tokenUsage = new ChatTokenUsage(120, 35);

        ChatCompletionResult result = new ChatCompletionResult(
            "Odpowiedź oparta na dokumencie.",
            "gpt-4o-mini",
            tokenUsage
        );

        assertThat(result.content()).isEqualTo("Odpowiedź oparta na dokumencie.");
        assertThat(result.model()).isEqualTo("gpt-4o-mini");
        assertThat(result.tokenUsage()).isEqualTo(tokenUsage);
    }

    @Test
    void shouldRejectNullContent() {
        assertThatThrownBy(() -> new ChatCompletionResult(
            null,
            "gpt-4o-mini",
            new ChatTokenUsage(1, 1)
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Chat completion content must not be null");
    }

    @Test
    void shouldRejectBlankContent() {
        assertThatThrownBy(() -> new ChatCompletionResult(
            " ",
            "gpt-4o-mini",
            new ChatTokenUsage(1, 1)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Chat completion content must not be blank");
    }

    @Test
    void shouldRejectNullModel() {
        assertThatThrownBy(() -> new ChatCompletionResult(
            "Answer",
            null,
            new ChatTokenUsage(1, 1)
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Chat completion model must not be null");
    }

    @Test
    void shouldRejectBlankModel() {
        assertThatThrownBy(() -> new ChatCompletionResult(
            "Answer",
            " ",
            new ChatTokenUsage(1, 1)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Chat completion model must not be blank");
    }

    @Test
    void shouldRejectNullTokenUsage() {
        assertThatThrownBy(() -> new ChatCompletionResult(
            "Answer",
            "gpt-4o-mini",
            null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Chat completion token usage must not be null");
    }

    @Test
    void shouldNotExposeCompletionContentInToString() {
        ChatCompletionResult result = new ChatCompletionResult(
            "Poufna odpowiedź modelu",
            "gpt-4o-mini",
            new ChatTokenUsage(10, 4)
        );

        String value = result.toString();

        assertThat(value)
            .contains("contentLength=23")
            .contains("model=gpt-4o-mini")
            .doesNotContain("Poufna odpowiedź modelu");
    }
}
