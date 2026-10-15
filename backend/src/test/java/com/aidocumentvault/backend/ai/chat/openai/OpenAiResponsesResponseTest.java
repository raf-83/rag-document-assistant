package com.aidocumentvault.backend.ai.chat.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiResponsesResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeTextResponseAndTokenUsage() throws Exception {
        String json = """
            {
              "id": "resp_123",
              "object": "response",
              "status": "completed",
              "model": "gpt-4o-mini-2024-07-18",
              "output": [
                {
                  "id": "msg_123",
                  "type": "message",
                  "status": "completed",
                  "role": "assistant",
                  "content": [
                    {
                      "type": "output_text",
                      "text": "Umowa została podpisana 15 maja 2025 roku.",
                      "annotations": []
                    }
                  ]
                }
              ],
              "usage": {
                "input_tokens": 1200,
                "input_tokens_details": {
                  "cached_tokens": 0
                },
                "output_tokens": 150,
                "output_tokens_details": {
                  "reasoning_tokens": 0
                },
                "total_tokens": 1350
              },
              "metadata": {}
            }
            """;

        OpenAiResponsesResponse response = objectMapper.readValue(
            json,
            OpenAiResponsesResponse.class
        );

        assertThat(response.id()).isEqualTo("resp_123");
        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.model()).isEqualTo("gpt-4o-mini-2024-07-18");
        assertThat(response.output()).hasSize(1);

        OpenAiOutputItem outputItem = response.output().getFirst();
        assertThat(outputItem.type()).isEqualTo("message");
        assertThat(outputItem.role()).isEqualTo("assistant");
        assertThat(outputItem.content()).hasSize(1);
        assertThat(outputItem.content().getFirst().type())
            .isEqualTo("output_text");
        assertThat(outputItem.content().getFirst().text())
            .isEqualTo("Umowa została podpisana 15 maja 2025 roku.");

        assertThat(response.usage().inputTokens()).isEqualTo(1200);
        assertThat(response.usage().outputTokens()).isEqualTo(150);
        assertThat(response.usage().totalTokens()).isEqualTo(1350);
    }

    @Test
    void shouldNotExposeGeneratedTextInToString() throws Exception {
        String generatedText = "Poufna odpowiedź na podstawie dokumentu";
        String json = """
            {
              "id": "resp_123",
              "status": "completed",
              "model": "gpt-4o-mini",
              "output": [
                {
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    {
                      "type": "output_text",
                      "text": "%s"
                    }
                  ]
                }
              ],
              "usage": {
                "input_tokens": 10,
                "output_tokens": 5,
                "total_tokens": 15
              }
            }
            """.formatted(generatedText);

        OpenAiResponsesResponse response = objectMapper.readValue(
            json,
            OpenAiResponsesResponse.class
        );

        assertThat(response.toString())
            .doesNotContain(generatedText)
            .contains("outputCount=1");
        assertThat(response.output().getFirst().toString())
            .doesNotContain(generatedText)
            .contains("contentCount=1");
        assertThat(response.output().getFirst().content().getFirst().toString())
            .doesNotContain(generatedText)
            .contains("textLength=" + generatedText.length());
    }
}
