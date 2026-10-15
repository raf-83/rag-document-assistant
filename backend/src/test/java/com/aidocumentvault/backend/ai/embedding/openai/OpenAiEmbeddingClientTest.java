package com.aidocumentvault.backend.ai.embedding.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aidocumentvault.backend.ai.config.AiEmbeddingProperties;
import com.aidocumentvault.backend.ai.embedding.exception.EmbeddingGenerationException;
import com.aidocumentvault.backend.ai.embedding.EmbeddingVector;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiEmbeddingClientTest {

    @Test
    void shouldCallOpenAiEmbeddingsApiAndReturnVectors() {
        // given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        RestClient restClient = builder
            .baseUrl("https://api.openai.com/v1")
            .defaultHeader(AUTHORIZATION, "Bearer test-api-key")
            .build();

        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(
            restClient,
            new AiEmbeddingProperties("text-embedding-3-small", 1536, 32)
        );

        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
            .andExpect(method(POST))
            .andExpect(header(AUTHORIZATION, "Bearer test-api-key"))
            .andExpect(content().contentType(APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "model": "text-embedding-3-small",
                  "input": ["Pierwszy chunk", "Drugi chunk"],
                  "encoding_format": "float",
                  "dimensions": 1536
                }
                """))
            .andRespond(withSuccess(
                embeddingResponseJson(2, 1536),
                APPLICATION_JSON
            ));

        // when
        List<EmbeddingVector> vectors = client.embed(List.of(
            "Pierwszy chunk",
            "Drugi chunk"
        ));

        // then
        assertThat(vectors).hasSize(2);
        assertThat(vectors)
            .extracting(EmbeddingVector::dimensions)
            .containsOnly(1536);

        server.verify();
    }

    @Test
    void shouldRejectBlankInput() {
        // given
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(
            RestClient.builder().baseUrl("https://api.openai.com/v1").build(),
            new AiEmbeddingProperties("text-embedding-3-small", 1536, 32)
        );

        // when / then
        assertThatThrownBy(() -> client.embed(List.of(" ")))
            .isInstanceOf(EmbeddingGenerationException.class)
            .hasMessageContaining("Embedding input texts must not contain blank values");
    }

    @Test
    void shouldThrowWhenOpenAiReturnsError() {
        // given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        RestClient restClient = builder
            .baseUrl("https://api.openai.com/v1")
            .defaultHeader(AUTHORIZATION, "Bearer test-api-key")
            .build();

        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(
            restClient,
            new AiEmbeddingProperties("text-embedding-3-small", 1536, 32)
        );

        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
            .andRespond(withServerError());

        // when / then
        assertThatThrownBy(() -> client.embed(List.of("Pierwszy chunk")))
            .isInstanceOf(EmbeddingGenerationException.class)
            .hasMessageContaining("OpenAI embedding request failed");

        server.verify();
    }

    @Test
    void shouldThrowWhenOpenAiResponseHasWrongDimensions() {
        // given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        RestClient restClient = builder
            .baseUrl("https://api.openai.com/v1")
            .defaultHeader(AUTHORIZATION, "Bearer test-api-key")
            .build();

        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(
            restClient,
            new AiEmbeddingProperties("text-embedding-3-small", 1536, 32)
        );

        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
            .andRespond(withSuccess(
                embeddingResponseJson(1, 10),
                APPLICATION_JSON
            ));

        // when / then
        assertThatThrownBy(() -> client.embed(List.of("Pierwszy chunk")))
            .isInstanceOf(EmbeddingGenerationException.class)
            .hasMessageContaining("OpenAI embedding dimensions do not match configured dimensions");

        server.verify();
    }

    private String embeddingResponseJson(int count, int dimensions) {
        String data = IntStream.range(0, count)
            .mapToObj(index -> """
                {
                  "object": "embedding",
                  "index": %d,
                  "embedding": %s
                }
                """.formatted(index, vectorJson(dimensions)))
            .collect(Collectors.joining(","));

        return """
            {
              "object": "list",
              "data": [%s],
              "model": "text-embedding-3-small",
              "usage": {
                "prompt_tokens": 10,
                "total_tokens": 10
              }
            }
            """.formatted(data);
    }

    private String vectorJson(int dimensions) {
        return IntStream.range(0, dimensions)
            .mapToObj(index -> String.valueOf(index * 0.001))
            .collect(Collectors.joining(",", "[", "]"));
    }
}
