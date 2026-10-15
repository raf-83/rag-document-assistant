package com.aidocumentvault.backend.ai.chat.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidocumentvault.backend.ai.chat.model.ChatMessage;
import com.aidocumentvault.backend.ai.chat.config.AiChatCompletionProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiResponsesRequestMapperTest {

    private final OpenAiResponsesRequestMapper mapper =
        new OpenAiResponsesRequestMapper(
            new AiChatCompletionProperties(
                "gpt-4o-mini",
                800
            )
        );

    @Test
    void shouldBuildRequestUsingConfiguredModelAndOutputLimit() {
        List<ChatMessage> messages = List.of(
            ChatMessage.system("Instrukcja systemowa"),
            ChatMessage.user("Pytanie użytkownika")
        );

        OpenAiResponsesRequest request = mapper.map(messages);

        assertThat(request.model()).isEqualTo("gpt-4o-mini");
        assertThat(request.maxOutputTokens()).isEqualTo(800);
        assertThat(request.input()).hasSize(2);
    }

    @Test
    void shouldMapAllChatMessageRolesToOpenAiRoles() {
        List<ChatMessage> messages = List.of(
            ChatMessage.system("Instrukcja systemowa"),
            ChatMessage.user("Pytanie użytkownika"),
            ChatMessage.assistant("Poprzednia odpowiedź")
        );

        OpenAiResponsesRequest request = mapper.map(messages);

        assertThat(request.input())
            .extracting(OpenAiInputMessage::role)
            .containsExactly(
                "developer",
                "user",
                "assistant"
            );
    }

    @Test
    void shouldMapMessageContentToInputText() {
        String messageContent = "Kiedy została podpisana umowa?";

        OpenAiResponsesRequest request = mapper.map(
            List.of(ChatMessage.user(messageContent))
        );

        OpenAiInputMessage inputMessage = request.input().getFirst();

        assertThat(inputMessage.content()).hasSize(1);
        assertThat(inputMessage.content().getFirst().type())
            .isEqualTo("input_text");
        assertThat(inputMessage.content().getFirst().text())
            .isEqualTo(messageContent);
    }

    @Test
    void shouldPreserveMessageOrder() {
        List<ChatMessage> messages = List.of(
            ChatMessage.system("Pierwsza wiadomość"),
            ChatMessage.user("Druga wiadomość"),
            ChatMessage.assistant("Trzecia wiadomość")
        );

        OpenAiResponsesRequest request = mapper.map(messages);

        List<String> mappedContents = request.input().stream()
            .flatMap(inputMessage -> inputMessage.content().stream())
            .map(OpenAiInputContent::text)
            .toList();

        assertThat(mappedContents)
            .containsExactly(
                "Pierwsza wiadomość",
                "Druga wiadomość",
                "Trzecia wiadomość"
            );
    }

    @Test
    void shouldRejectNullMessageList() {
        assertThatThrownBy(() -> mapper.map(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Chat messages must not be null");
    }

    @Test
    void shouldRejectEmptyMessageList() {
        assertThatThrownBy(() -> mapper.map(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Chat messages must not be empty");
    }

    @Test
    void shouldRejectNullMessageElement() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("Instrukcja systemowa"));
        messages.add(null);

        assertThatThrownBy(() -> mapper.map(messages))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(
                "Chat messages must not contain null values"
            );
    }

    @Test
    void shouldRejectNullProperties() {
        assertThatThrownBy(
            () -> new OpenAiResponsesRequestMapper(null)
        )
            .isInstanceOf(NullPointerException.class)
            .hasMessage(
                "Chat completion properties must not be null"
            );
    }
}
