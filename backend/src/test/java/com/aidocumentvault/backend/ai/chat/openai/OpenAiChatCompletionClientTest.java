package com.aidocumentvault.backend.ai.chat.openai;

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

import com.aidocumentvault.backend.ai.chat.config.AiChatCompletionProperties;
import com.aidocumentvault.backend.ai.chat.exception.ChatCompletionUnavailableException;
import com.aidocumentvault.backend.ai.chat.exception.InvalidChatCompletionResponseException;
import com.aidocumentvault.backend.ai.chat.model.ChatCompletionResult;
import com.aidocumentvault.backend.ai.chat.model.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiChatCompletionClientTest {

    private MockRestServiceServer server;
    private OpenAiChatCompletionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();

        server = MockRestServiceServer
            .bindTo(builder)
            .build();

        RestClient restClient = builder
            .baseUrl("https://api.openai.com/v1")
            .defaultHeader(
                AUTHORIZATION,
                "Bearer test-api-key"
            )
            .build();

        OpenAiResponsesRequestMapper requestMapper =
            new OpenAiResponsesRequestMapper(
                new AiChatCompletionProperties(
                    "gpt-4o-mini",
                    800
                )
            );

        client = new OpenAiChatCompletionClient(
            restClient,
            requestMapper
        );
    }

    @Test
    void shouldCallResponsesApiAndReturnCompletionResult() {
        server.expect(
            requestTo(
                "https://api.openai.com/v1/responses"
            )
        )
            .andExpect(method(POST))
            .andExpect(
                header(
                    AUTHORIZATION,
                    "Bearer test-api-key"
                )
            )
            .andExpect(
                content().contentType(APPLICATION_JSON)
            )
            .andExpect(
                content().json(
                    """
                    {
                      "model": "gpt-4o-mini",
                      "input": [
                        {
                          "role": "developer",
                          "content": [
                            {
                              "type": "input_text",
                              "text": "Odpowiadaj tylko na podstawie źródeł."
                            }
                          ]
                        },
                        {
                          "role": "user",
                          "content": [
                            {
                              "type": "input_text",
                              "text": "Kiedy podpisano umowę?"
                            }
                          ]
                        }
                      ],
                      "max_output_tokens": 800
                    }
                    """
                )
            )
            .andRespond(
                withSuccess(
                    completedResponseJson(),
                    APPLICATION_JSON
                )
            );

        ChatCompletionResult result = client.complete(
            List.of(
                ChatMessage.system(
                    "Odpowiadaj tylko na podstawie źródeł."
                ),
                ChatMessage.user(
                    "Kiedy podpisano umowę?"
                )
            )
        );

        assertThat(result.content())
            .isEqualTo(
                "Umowę podpisano 15 maja 2025 roku."
            );

        assertThat(result.model())
            .isEqualTo("gpt-4o-mini");

        assertThat(result.tokenUsage().inputTokens())
            .isEqualTo(120);

        assertThat(result.tokenUsage().outputTokens())
            .isEqualTo(35);

        assertThat(result.tokenUsage().totalTokens())
            .isEqualTo(155);

        server.verify();
    }

    @Test
    void shouldFindOutputTextOutsideFirstOutputItem() {
        server.expect(
            requestTo(
                "https://api.openai.com/v1/responses"
            )
        )
            .andRespond(
                withSuccess(
                    """
                    {
                      "id": "resp_123",
                      "status": "completed",
                      "model": "gpt-4o-mini",
                      "output": [
                        {
                          "type": "reasoning",
                          "content": []
                        },
                        {
                          "type": "message",
                          "role": "assistant",
                          "content": [
                            {
                              "type": "output_text",
                              "text": "Znaleziona odpowiedź."
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
                    """,
                    APPLICATION_JSON
                )
            );

        ChatCompletionResult result = client.complete(
            List.of(ChatMessage.user("Pytanie"))
        );

        assertThat(result.content())
            .isEqualTo("Znaleziona odpowiedź.");

        server.verify();
    }

    @Test
    void shouldRejectNullResponseBody() {
        server.expect(
            requestTo(
                "https://api.openai.com/v1/responses"
            )
        )
            .andRespond(
                withSuccess("", APPLICATION_JSON)
            );

        assertThatThrownBy(
            () -> client.complete(
                List.of(ChatMessage.user("Pytanie"))
            )
        )
            .isInstanceOf(
                InvalidChatCompletionResponseException.class
            )
            .hasMessage(
                "OpenAI chat completion response "
                    + "must not be null"
            );

        server.verify();
    }

    @Test
    void shouldRejectNonCompletedStatus() {
        server.expect(
            requestTo(
                "https://api.openai.com/v1/responses"
            )
        )
            .andRespond(
                withSuccess(
                    responseJson(
                        "incomplete",
                        "gpt-4o-mini",
                        outputJson(),
                        usageJson(10, 5, 15)
                    ),
                    APPLICATION_JSON
                )
            );

        assertThatThrownBy(
            () -> client.complete(
                List.of(ChatMessage.user("Pytanie"))
            )
        )
            .isInstanceOf(
                InvalidChatCompletionResponseException.class
            )
            .hasMessageContaining(
                "status must be completed"
            );

        server.verify();
    }

    @Test
    void shouldRejectMissingOutputText() {
        server.expect(
            requestTo(
                "https://api.openai.com/v1/responses"
            )
        )
            .andRespond(
                withSuccess(
                    responseJson(
                        "completed",
                        "gpt-4o-mini",
                        """
                        [
                          {
                            "type": "message",
                            "role": "assistant",
                            "content": [
                              {
                                "type": "refusal",
                                "text": "Brak odpowiedzi"
                              }
                            ]
                          }
                        ]
                        """,
                        usageJson(10, 5, 15)
                    ),
                    APPLICATION_JSON
                )
            );

        assertThatThrownBy(
            () -> client.complete(
                List.of(ChatMessage.user("Pytanie"))
            )
        )
            .isInstanceOf(
                InvalidChatCompletionResponseException.class
            )
            .hasMessageContaining(
                "does not contain output_text"
            );

        server.verify();
    }

    @Test
    void shouldRejectBlankModel() {
        server.expect(
            requestTo(
                "https://api.openai.com/v1/responses"
            )
        )
            .andRespond(
                withSuccess(
                    responseJson(
                        "completed",
                        " ",
                        outputJson(),
                        usageJson(10, 5, 15)
                    ),
                    APPLICATION_JSON
                )
            );

        assertThatThrownBy(
            () -> client.complete(
                List.of(ChatMessage.user("Pytanie"))
            )
        )
            .isInstanceOf(
                InvalidChatCompletionResponseException.class
            )
            .hasMessageContaining(
                "model must not be blank"
            );

        server.verify();
    }

    @Test
    void shouldRejectMissingUsage() {
        server.expect(
            requestTo(
                "https://api.openai.com/v1/responses"
            )
        )
            .andRespond(
                withSuccess(
                    responseJson(
                        "completed",
                        "gpt-4o-mini",
                        outputJson(),
                        "null"
                    ),
                    APPLICATION_JSON
                )
            );

        assertThatThrownBy(
            () -> client.complete(
                List.of(ChatMessage.user("Pytanie"))
            )
        )
            .isInstanceOf(
                InvalidChatCompletionResponseException.class
            )
            .hasMessageContaining(
                "usage must not be null"
            );

        server.verify();
    }

    @Test
    void shouldRejectMissingTokenCounts() {
        server.expect(
            requestTo(
                "https://api.openai.com/v1/responses"
            )
        )
            .andRespond(
                withSuccess(
                    responseJson(
                        "completed",
                        "gpt-4o-mini",
                        outputJson(),
                        """
                        {
                          "input_tokens": null,
                          "output_tokens": 5,
                          "total_tokens": 5
                        }
                        """
                    ),
                    APPLICATION_JSON
                )
            );

        assertThatThrownBy(
            () -> client.complete(
                List.of(ChatMessage.user("Pytanie"))
            )
        )
            .isInstanceOf(
                InvalidChatCompletionResponseException.class
            )
            .hasMessageContaining(
                "token counts must not be null"
            );

        server.verify();
    }

    @Test
    void shouldRejectInconsistentTotalTokenCount() {
        server.expect(
            requestTo(
                "https://api.openai.com/v1/responses"
            )
        )
            .andRespond(
                withSuccess(
                    responseJson(
                        "completed",
                        "gpt-4o-mini",
                        outputJson(),
                        usageJson(10, 5, 100)
                    ),
                    APPLICATION_JSON
                )
            );

        assertThatThrownBy(
            () -> client.complete(
                List.of(ChatMessage.user("Pytanie"))
            )
        )
            .isInstanceOf(
                InvalidChatCompletionResponseException.class
            )
            .hasMessageContaining(
                "total token count does not match"
            );

        server.verify();
    }

    @Test
    void shouldMapRestClientErrorToUnavailableException() {
        server.expect(
            requestTo(
                "https://api.openai.com/v1/responses"
            )
        )
            .andRespond(withServerError());

        assertThatThrownBy(
            () -> client.complete(
                List.of(ChatMessage.user("Pytanie"))
            )
        )
            .isInstanceOf(
                ChatCompletionUnavailableException.class
            )
            .hasMessage(
                "OpenAI chat completion request failed"
            );

        server.verify();
    }

    @Test
    void shouldCombineAllOutputTextFragments() {
        server.expect(
                requestTo(
                    "https://api.openai.com/v1/responses"
                )
            )
            .andRespond(
                withSuccess(
                    responseJson(
                        "completed",
                        "gpt-4o-mini",
                        """
                        [
                          {
                            "type": "message",
                            "role": "assistant",
                            "content": [
                              {
                                "type": "output_text",
                                "text": "Pierwsza część. "
                              },
                              {
                                "type": "output_text",
                                "text": "Druga część."
                              }
                            ]
                          }
                        ]
                        """,
                        usageJson(10, 5, 15)
                    ),
                    APPLICATION_JSON
                )
            );

        ChatCompletionResult result = client.complete(
            List.of(ChatMessage.user("Pytanie"))
        );

        assertThat(result.content())
            .isEqualTo(
                "Pierwsza część. Druga część."
            );

        server.verify();
    }

    @Test
    void shouldRejectMissingOutput() {
        server.expect(
                requestTo(
                    "https://api.openai.com/v1/responses"
                )
            )
            .andRespond(
                withSuccess(
                    responseJson(
                        "completed",
                        "gpt-4o-mini",
                        "null",
                        usageJson(10, 5, 15)
                    ),
                    APPLICATION_JSON
                )
            );

        assertThatThrownBy(
            () -> client.complete(
                List.of(ChatMessage.user("Pytanie"))
            )
        )
            .isInstanceOf(
                InvalidChatCompletionResponseException.class
            )
            .hasMessage(
                "OpenAI chat completion response output "
                    + "must not be empty"
            );

        server.verify();
    }

    @Test
    void shouldRejectEmptyOutput() {
        server.expect(
                requestTo(
                    "https://api.openai.com/v1/responses"
                )
            )
            .andRespond(
                withSuccess(
                    responseJson(
                        "completed",
                        "gpt-4o-mini",
                        "[]",
                        usageJson(10, 5, 15)
                    ),
                    APPLICATION_JSON
                )
            );

        assertThatThrownBy(
            () -> client.complete(
                List.of(ChatMessage.user("Pytanie"))
            )
        )
            .isInstanceOf(
                InvalidChatCompletionResponseException.class
            )
            .hasMessage(
                "OpenAI chat completion response output "
                    + "must not be empty"
            );

        server.verify();
    }

    @Test
    void shouldRejectNegativeTokenCounts() {
        server.expect(
                requestTo(
                    "https://api.openai.com/v1/responses"
                )
            )
            .andRespond(
                withSuccess(
                    responseJson(
                        "completed",
                        "gpt-4o-mini",
                        outputJson(),
                        usageJson(-1, 5, 4)
                    ),
                    APPLICATION_JSON
                )
            );

        assertThatThrownBy(
            () -> client.complete(
                List.of(ChatMessage.user("Pytanie"))
            )
        )
            .isInstanceOf(
                InvalidChatCompletionResponseException.class
            )
            .hasMessage(
                "OpenAI chat completion token counts "
                    + "must not be negative"
            );

        server.verify();
    }

    private String completedResponseJson() {
        return responseJson(
            "completed",
            "gpt-4o-mini",
            outputJson(),
            usageJson(120, 35, 155)
        );
    }

    private String responseJson(
        String status,
        String model,
        String output,
        String usage
    ) {
        return """
            {
              "id": "resp_123",
              "status": "%s",
              "model": "%s",
              "output": %s,
              "usage": %s
            }
            """.formatted(
                status,
                model,
                output,
                usage
            );
    }

    private String outputJson() {
        return """
            [
              {
                "type": "message",
                "role": "assistant",
                "content": [
                  {
                    "type": "output_text",
                    "text": "Umowę podpisano 15 maja 2025 roku."
                  }
                ]
              }
            ]
            """;
    }

    private String usageJson(
        int inputTokens,
        int outputTokens,
        int totalTokens
    ) {
        return """
            {
              "input_tokens": %d,
              "output_tokens": %d,
              "total_tokens": %d
            }
            """.formatted(
                inputTokens,
                outputTokens,
                totalTokens
            );
    }
}
