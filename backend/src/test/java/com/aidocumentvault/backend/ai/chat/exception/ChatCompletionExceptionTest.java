package com.aidocumentvault.backend.ai.chat.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatCompletionExceptionTest {

    @Test
    void shouldPreserveMessageAndCause() {
        RuntimeException cause = new RuntimeException("HTTP client error");

        ChatCompletionException exception = new ChatCompletionException(
            "Chat completion failed",
            cause
        );

        assertThat(exception)
            .hasMessage("Chat completion failed")
            .hasCause(cause);
    }

    @Test
    void shouldRepresentUnavailableProvider() {
        RuntimeException cause = new RuntimeException("Connection timeout");

        ChatCompletionUnavailableException exception =
            new ChatCompletionUnavailableException(
                "Chat completion provider is unavailable",
                cause
            );

        assertThat(exception)
            .isInstanceOf(ChatCompletionException.class)
            .hasMessage("Chat completion provider is unavailable")
            .hasCause(cause);
    }

    @Test
    void shouldRepresentInvalidProviderResponse() {
        InvalidChatCompletionResponseException exception =
            new InvalidChatCompletionResponseException(
                "Chat completion response does not contain text"
            );

        assertThat(exception)
            .isInstanceOf(ChatCompletionException.class)
            .hasMessage(
                "Chat completion response does not contain text"
            )
            .hasNoCause();
    }
}
