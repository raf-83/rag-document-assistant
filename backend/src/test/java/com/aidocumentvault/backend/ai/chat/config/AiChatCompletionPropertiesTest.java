package com.aidocumentvault.backend.ai.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AiChatCompletionPropertiesTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldUseDefaultValues() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            AiChatCompletionProperties properties = context.getBean(
                AiChatCompletionProperties.class
            );

            assertThat(properties.model()).isEqualTo("gpt-4o-mini");
            assertThat(properties.maxOutputTokens()).isEqualTo(800);
        });
    }

    @Test
    void shouldBindConfiguredValues() {
        contextRunner
            .withPropertyValues(
                "app.ai.chat-completion.model=custom-chat-model",
                "app.ai.chat-completion.max-output-tokens=1200"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();

                AiChatCompletionProperties properties = context.getBean(
                    AiChatCompletionProperties.class
                );

                assertThat(properties.model())
                    .isEqualTo("custom-chat-model");
                assertThat(properties.maxOutputTokens()).isEqualTo(1200);
            });
    }

    @Test
    void shouldTrimModel() {
        AiChatCompletionProperties properties =
            new AiChatCompletionProperties("  gpt-4o-mini  ", 800);

        assertThat(properties.model()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void shouldAcceptOutputTokenLimitBoundaryValues() {
        AiChatCompletionProperties minimumProperties =
            new AiChatCompletionProperties("gpt-4o-mini", 1);
        AiChatCompletionProperties maximumProperties =
            new AiChatCompletionProperties("gpt-4o-mini", 4096);

        assertThat(minimumProperties.maxOutputTokens()).isEqualTo(1);
        assertThat(maximumProperties.maxOutputTokens()).isEqualTo(4096);
    }

    @Test
    void shouldRejectNullModel() {
        assertThatThrownBy(
            () -> new AiChatCompletionProperties(null, 800)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "Chat completion model must not be blank"
            );
    }

    @Test
    void shouldRejectBlankModel() {
        assertThatThrownBy(
            () -> new AiChatCompletionProperties(" ", 800)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "Chat completion model must not be blank"
            );
    }

    @Test
    void shouldRejectTooSmallOutputTokenLimit() {
        assertThatThrownBy(
            () -> new AiChatCompletionProperties("gpt-4o-mini", 0)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "Chat completion maxOutputTokens must be between 1 and 4096"
            );
    }

    @Test
    void shouldRejectTooLargeOutputTokenLimit() {
        assertThatThrownBy(
            () -> new AiChatCompletionProperties("gpt-4o-mini", 4097)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "Chat completion maxOutputTokens must be between 1 and 4096"
            );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiChatCompletionProperties.class)
    static class TestConfiguration {
    }
}
