package com.aidocumentvault.backend.ai.chat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChatTokenUsageTest {

    @Test
    void shouldCalculateTotalTokenCount() {
        ChatTokenUsage usage = new ChatTokenUsage(120, 35);

        assertThat(usage.totalTokens()).isEqualTo(155);
    }

    @Test
    void shouldAllowZeroTokenCounts() {
        ChatTokenUsage usage = new ChatTokenUsage(0, 0);

        assertThat(usage.totalTokens()).isZero();
    }

    @Test
    void shouldRejectNegativeInputTokenCount() {
        assertThatThrownBy(() -> new ChatTokenUsage(-1, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Input token count must not be negative");
    }

    @Test
    void shouldRejectNegativeOutputTokenCount() {
        assertThatThrownBy(() -> new ChatTokenUsage(0, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Output token count must not be negative");
    }

    @Test
    void shouldDetectTotalTokenCountOverflow() {
        ChatTokenUsage usage = new ChatTokenUsage(Integer.MAX_VALUE, 1);

        assertThatThrownBy(usage::totalTokens)
            .isInstanceOf(ArithmeticException.class);
    }
}
