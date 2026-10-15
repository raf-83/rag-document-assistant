package com.aidocumentvault.backend.ai.config;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class OpenAiRestClientConfig {

    private final AiOpenAiProperties aiOpenAiProperties;

    @Bean
    public RestClient openAiRestClient(RestClient.Builder restClientBuilder) {
        Duration timeout = Duration.ofSeconds(aiOpenAiProperties.timeoutSeconds());

        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return restClientBuilder
            .baseUrl(aiOpenAiProperties.baseUrl())
            .requestFactory(requestFactory)
            .defaultHeader(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + aiOpenAiProperties.apiKey()
            )
            .defaultHeader(
                HttpHeaders.CONTENT_TYPE,
                MediaType.APPLICATION_JSON_VALUE
            )
            .build();
    }
}
