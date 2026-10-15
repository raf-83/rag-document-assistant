package com.aidocumentvault.backend.ai.chat.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiResponsesRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeRequestUsingOpenAiFieldNames() throws Exception {
        OpenAiResponsesRequest request = new OpenAiResponsesRequest(
            "gpt-4o-mini",
            List.of(
                new OpenAiInputMessage(
                    "developer",
                    List.of(new OpenAiInputContent(
                        "input_text",
                        "Odpowiadaj tylko na podstawie źródeł."
                    ))
                ),
                new OpenAiInputMessage(
                    "user",
                    List.of(new OpenAiInputContent(
                        "input_text",
                        "Kiedy podpisano umowę?"
                    ))
                )
            ),
            800
        );

        JsonNode json = objectMapper.readTree(
            objectMapper.writeValueAsString(request)
        );

        assertThat(json.get("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(json.get("max_output_tokens").asInt()).isEqualTo(800);
        assertThat(json.get("input")).hasSize(2);
        assertThat(json.at("/input/0/role").asText()).isEqualTo("developer");
        assertThat(json.at("/input/0/content/0/type").asText())
            .isEqualTo("input_text");
        assertThat(json.at("/input/1/role").asText()).isEqualTo("user");
        assertThat(json.at("/input/1/content/0/text").asText())
            .isEqualTo("Kiedy podpisano umowę?");
    }

    @Test
    void shouldNotExposeInputTextInToString() {
        String sensitiveText = "Poufna treść dokumentu";
        OpenAiResponsesRequest request = new OpenAiResponsesRequest(
            "gpt-4o-mini",
            List.of(new OpenAiInputMessage(
                "user",
                List.of(new OpenAiInputContent("input_text", sensitiveText))
            )),
            800
        );

        assertThat(request.toString())
            .doesNotContain(sensitiveText)
            .contains("model=gpt-4o-mini")
            .contains("inputCount=1")
            .contains("maxOutputTokens=800");
    }
}
