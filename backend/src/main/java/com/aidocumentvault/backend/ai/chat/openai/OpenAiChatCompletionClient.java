package com.aidocumentvault.backend.ai.chat.openai;

import com.aidocumentvault.backend.ai.chat.ChatCompletionClient;
import com.aidocumentvault.backend.ai.chat.exception.ChatCompletionUnavailableException;
import com.aidocumentvault.backend.ai.chat.exception.InvalidChatCompletionResponseException;
import com.aidocumentvault.backend.ai.chat.model.ChatCompletionResult;
import com.aidocumentvault.backend.ai.chat.model.ChatMessage;
import com.aidocumentvault.backend.ai.chat.model.ChatTokenUsage;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class OpenAiChatCompletionClient implements ChatCompletionClient {

    private static final String RESPONSES_ENDPOINT = "/responses";
    private static final String COMPLETED_STATUS = "completed";
    private static final String MESSAGE_OUTPUT_TYPE = "message";
    private static final String ASSISTANT_ROLE = "assistant";
    private static final String OUTPUT_TEXT_TYPE = "output_text";

    private final RestClient openAiRestClient;
    private final OpenAiResponsesRequestMapper requestMapper;

    @Override
    public ChatCompletionResult complete(List<ChatMessage> messages) {
        OpenAiResponsesRequest request = requestMapper.map(messages);

        try {
            OpenAiResponsesResponse response =
                openAiRestClient
                    .post()
                    .uri(RESPONSES_ENDPOINT)
                    .body(request)
                    .retrieve()
                    .body(OpenAiResponsesResponse.class);

            return toChatCompletionResult(response);
        } catch (RestClientException exception) {
            throw new ChatCompletionUnavailableException(
                "OpenAI chat completion request failed",
                exception
            );
        }
    }

    private ChatCompletionResult toChatCompletionResult(
        OpenAiResponsesResponse response
    ) {
        if (response == null) {
            throw new InvalidChatCompletionResponseException(
                "OpenAI chat completion response must not be null"
            );
        }

        validateCompletedStatus(response.status());
        validateModel(response.model());

        String content = collectOutputText(response.output());
        ChatTokenUsage tokenUsage = toTokenUsage(response.usage());

        return new ChatCompletionResult(
            content,
            response.model(),
            tokenUsage
        );
    }

    private void validateCompletedStatus(String status) {
        if (!COMPLETED_STATUS.equals(status)) {
            throw new InvalidChatCompletionResponseException(
                "OpenAI chat completion response status "
                    + "must be completed"
            );
        }
    }

    private void validateModel(String model) {
        if (model == null || model.isBlank()) {
            throw new InvalidChatCompletionResponseException(
                "OpenAI chat completion response model "
                    + "must not be blank"
            );
        }
    }

    private String collectOutputText(
        List<OpenAiOutputItem> output
    ) {
        if (output == null || output.isEmpty()) {
            throw new InvalidChatCompletionResponseException(
                "OpenAI chat completion response output "
                    + "must not be empty"
            );
        }

        String content = output.stream()
            .filter(Objects::nonNull)
            .filter(this::isAssistantMessage)
            .flatMap(this::contentStream)
            .filter(Objects::nonNull)
            .filter(this::isNonBlankOutputText)
            .map(OpenAiOutputContent::text)
            .collect(Collectors.joining());

        if (content.isBlank()) {
            throw new InvalidChatCompletionResponseException(
                "OpenAI chat completion response "
                    + "does not contain output_text"
            );
        }

        return content;
    }

    private boolean isAssistantMessage(
        OpenAiOutputItem outputItem
    ) {
        return MESSAGE_OUTPUT_TYPE.equals(outputItem.type())
            && ASSISTANT_ROLE.equals(outputItem.role());
    }

    private Stream<OpenAiOutputContent> contentStream(
        OpenAiOutputItem outputItem
    ) {
        if (
            outputItem.content() == null
                || outputItem.content().isEmpty()
        ) {
            return Stream.empty();
        }

        return outputItem.content().stream();
    }

    private boolean isNonBlankOutputText(
        OpenAiOutputContent outputContent
    ) {
        return OUTPUT_TEXT_TYPE.equals(outputContent.type())
            && outputContent.text() != null
            && !outputContent.text().isBlank();
    }

    private ChatTokenUsage toTokenUsage(
        OpenAiResponseUsage usage
    ) {
        if (usage == null) {
            throw new InvalidChatCompletionResponseException(
                "OpenAI chat completion response usage "
                    + "must not be null"
            );
        }

        Integer inputTokens = usage.inputTokens();
        Integer outputTokens = usage.outputTokens();

        if (inputTokens == null || outputTokens == null) {
            throw new InvalidChatCompletionResponseException(
                "OpenAI chat completion token counts "
                    + "must not be null"
            );
        }

        if (inputTokens < 0 || outputTokens < 0) {
            throw new InvalidChatCompletionResponseException(
                "OpenAI chat completion token counts "
                    + "must not be negative"
            );
        }

        validateTotalTokens(
            inputTokens,
            outputTokens,
            usage.totalTokens()
        );

        return new ChatTokenUsage(
            inputTokens,
            outputTokens
        );
    }

    private void validateTotalTokens(
        int inputTokens,
        int outputTokens,
        Integer totalTokens
    ) {
        if (totalTokens == null) {
            return;
        }

        long expectedTotal =
            (long) inputTokens + outputTokens;

        if (totalTokens.longValue() != expectedTotal) {
            throw new InvalidChatCompletionResponseException(
                "OpenAI chat completion total token count "
                    + "does not match input and output counts"
            );
        }
    }
}
