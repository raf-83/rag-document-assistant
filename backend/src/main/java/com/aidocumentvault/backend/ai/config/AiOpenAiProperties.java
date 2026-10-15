package com.aidocumentvault.backend.ai.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.ai.openai")
public record AiOpenAiProperties(
    String apiKey,

    @DefaultValue("https://api.openai.com/v1")
    String baseUrl,

    @DefaultValue("30")
    int timeoutSeconds
) {

    public AiOpenAiProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenAI API key must not be blank");
        }

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("OpenAI baseUrl must not be blank");
        }

        try {
            URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("OpenAI baseUrl must be a valid URI", exception);
        }

        if (timeoutSeconds < 1 || timeoutSeconds > 120) {
            throw new IllegalArgumentException(
                "OpenAI timeoutSeconds must be between 1 and 120"
            );
        }

        apiKey = apiKey.trim();
        baseUrl = baseUrl.trim();
    }
}
