package com.aidocumentvault.backend.ai.chat;

import com.aidocumentvault.backend.ai.chat.model.ChatCompletionResult;
import com.aidocumentvault.backend.ai.chat.model.ChatMessage;
import java.util.List;

public interface ChatCompletionClient {

    ChatCompletionResult complete(List<ChatMessage> messages);
}
