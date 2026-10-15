package com.aidocumentvault.backend.ai.embedding.openai;

import com.aidocumentvault.backend.ai.config.AiEmbeddingProperties;
import com.aidocumentvault.backend.ai.embedding.EmbeddingClient;
import com.aidocumentvault.backend.ai.embedding.exception.EmbeddingGenerationException;
import com.aidocumentvault.backend.ai.embedding.EmbeddingVector;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final String EMBEDDINGS_ENDPOINT = "/embeddings";
    private static final String ENCODING_FORMAT_FLOAT = "float";

    private final RestClient openAiRestClient;
    private final AiEmbeddingProperties aiEmbeddingProperties;

    @Override
    public List<EmbeddingVector> embed(List<String> texts) {
        validateInput(texts);

        OpenAiEmbeddingRequest request = new OpenAiEmbeddingRequest(
            aiEmbeddingProperties.model(),
            texts,
            ENCODING_FORMAT_FLOAT,
            aiEmbeddingProperties.dimensions()
        );

        try {
            OpenAiEmbeddingResponse response = openAiRestClient
                .post()
                .uri(EMBEDDINGS_ENDPOINT)
                .body(request)
                .retrieve()
                .body(OpenAiEmbeddingResponse.class);

            return toEmbeddingVectors(response, texts.size());
        } catch (RestClientException exception) {
            throw new EmbeddingGenerationException(
                "OpenAI embedding request failed",
                exception
            );
        }
    }

    private void validateInput(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new EmbeddingGenerationException(
                "Embedding input texts must not be empty"
            );
        }

        boolean containsBlankText = texts.stream()
            .anyMatch(text -> text == null || text.isBlank());

        if (containsBlankText) {
            throw new EmbeddingGenerationException(
                "Embedding input texts must not contain blank values"
            );
        }
    }

    private List<EmbeddingVector> toEmbeddingVectors(
        OpenAiEmbeddingResponse response,
        int expectedSize
    ) {
        if (response == null) {
            throw new EmbeddingGenerationException(
                "OpenAI embedding response must not be null"
            );
        }

        if (response.data() == null || response.data().isEmpty()) {
            throw new EmbeddingGenerationException(
                "OpenAI embedding response data must not be empty"
            );
        }

        if (response.data().size() != expectedSize) {
            throw new EmbeddingGenerationException(
                "OpenAI embedding response size does not match input size"
            );
        }

        validateResponseIndexes(response.data(), expectedSize);

        return response.data()
            .stream()
            .sorted(Comparator.comparing(OpenAiEmbeddingData::index))
            .map(this::toEmbeddingVector)
            .toList();
    }

    private EmbeddingVector toEmbeddingVector(OpenAiEmbeddingData data) {
        if (data == null || data.embedding() == null || data.embedding().isEmpty()) {
            throw new EmbeddingGenerationException(
                "OpenAI embedding item must not be empty"
            );
        }

        if (data.index() == null) {
            throw new EmbeddingGenerationException(
                "OpenAI embedding item index must not be null"
            );
        }

        if (data.embedding().size() != aiEmbeddingProperties.dimensions()) {
            throw new EmbeddingGenerationException(
                "OpenAI embedding dimensions do not match configured dimensions"
            );
        }

        float[] values = new float[data.embedding().size()];

        for (int index = 0; index < data.embedding().size(); index++) {
            Double value = data.embedding().get(index);

            if (value == null) {
                throw new EmbeddingGenerationException(
                    "OpenAI embedding value must not be null"
                );
            }

            values[index] = value.floatValue();
        }

        return new EmbeddingVector(values);
    }

    private void validateResponseIndexes(
        List<OpenAiEmbeddingData> data,
        int expectedSize
    ) {
        boolean[] seenIndexes = new boolean[expectedSize];

        for (OpenAiEmbeddingData item : data) {
            if (item == null) {
                throw new EmbeddingGenerationException(
                    "OpenAI embedding item must not be null"
                );
            }

            if (item.index() == null) {
                throw new EmbeddingGenerationException(
                    "OpenAI embedding item index must not be null"
                );
            }

            int index = item.index();

            if (index < 0 || index >= expectedSize) {
                throw new EmbeddingGenerationException(
                    "OpenAI embedding item index is out of expected range"
                );
            }

            if (seenIndexes[index]) {
                throw new EmbeddingGenerationException(
                    "OpenAI embedding response contains duplicated indexes"
                );
            }

            seenIndexes[index] = true;
        }
    }
}
