package com.aidocumentvault.backend.ai.chat.openai;

import com.aidocumentvault.backend.ai.chat.model.ChatMessage;
import com.aidocumentvault.backend.ai.chat.model.ChatMessageRole;
import com.aidocumentvault.backend.ai.chat.config.AiChatCompletionProperties;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class OpenAiResponsesRequestMapper {

    private static final String DEVELOPER_ROLE = "developer";
    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";
    private static final String INPUT_TEXT_TYPE = "input_text";

    private final AiChatCompletionProperties properties;

    public OpenAiResponsesRequestMapper(AiChatCompletionProperties properties) {
        this.properties = Objects.requireNonNull(
            properties,
            "Chat completion properties must not be null"
        );
    }

    public OpenAiResponsesRequest map(List<ChatMessage> messages) {
        Objects.requireNonNull(
            messages,
            "Chat messages must not be null"
        );

        if (messages.isEmpty()) {
            throw new IllegalArgumentException(
                "Chat messages must not be empty"
            );
        }

        List<OpenAiInputMessage> input = messages.stream()
            .map(this::mapMessage)
            .toList();

        return new OpenAiResponsesRequest(
            properties.model(),
            input,
            properties.maxOutputTokens()
        );
    }

    private OpenAiInputMessage mapMessage(ChatMessage message) {
        Objects.requireNonNull(
            message,
            "Chat messages must not contain null values"
        );

        OpenAiInputContent inputContent = new OpenAiInputContent(
            INPUT_TEXT_TYPE,
            message.content()
        );

        return new OpenAiInputMessage(
            mapRole(message.role()),
            List.of(inputContent)
        );
    }

    private String mapRole(
        ChatMessageRole role
    ) {
        return switch (role) {
            case SYSTEM -> DEVELOPER_ROLE;
            case USER -> USER_ROLE;
            case ASSISTANT -> ASSISTANT_ROLE;
        };
    }
}
