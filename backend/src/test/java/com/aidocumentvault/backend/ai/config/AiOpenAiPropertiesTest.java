package com.aidocumentvault.backend.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiOpenAiPropertiesTest {

    @Test
    void shouldCreateValidProperties() {
        // when
        AiOpenAiProperties properties = new AiOpenAiProperties(
            "test-api-key",
            "https://api.openai.com/v1",
            30
        );

        // then
        assertThat(properties.apiKey()).isEqualTo("test-api-key");
        assertThat(properties.baseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(properties.timeoutSeconds()).isEqualTo(30);
    }

    @Test
    void shouldRejectBlankApiKey() {
        assertThatThrownBy(() -> new AiOpenAiProperties(
            " ",
            "https://api.openai.com/v1",
            30
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OpenAI API key must not be blank");
    }

    @Test
    void shouldRejectBlankBaseUrl() {
        assertThatThrownBy(() -> new AiOpenAiProperties(
            "test-api-key",
            " ",
            30
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OpenAI baseUrl must not be blank");
    }

    @Test
    void shouldRejectInvalidBaseUrl() {
        assertThatThrownBy(() -> new AiOpenAiProperties(
            "test-api-key",
            "not a valid uri",
            30
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OpenAI baseUrl must be a valid URI");
    }

    @Test
    void shouldRejectTooSmallTimeout() {
        assertThatThrownBy(() -> new AiOpenAiProperties(
            "test-api-key",
            "https://api.openai.com/v1",
            0
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OpenAI timeoutSeconds must be between 1 and 120");
    }

    @Test
    void shouldRejectTooLargeTimeout() {
        assertThatThrownBy(() -> new AiOpenAiProperties(
            "test-api-key",
            "https://api.openai.com/v1",
            121
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OpenAI timeoutSeconds must be between 1 and 120");
    }
}
