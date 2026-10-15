package com.aidocumentvault.backend;

import com.aidocumentvault.backend.ai.chat.config.AiChatCompletionProperties;
import com.aidocumentvault.backend.ai.config.AiChatProperties;
import com.aidocumentvault.backend.ai.config.AiEmbeddingProperties;
import com.aidocumentvault.backend.ai.config.AiHybridSearchProperties;
import com.aidocumentvault.backend.ai.config.AiOpenAiProperties;
import com.aidocumentvault.backend.config.properties.OcrProperties;
import com.aidocumentvault.backend.config.properties.SecurityProperties;
import com.aidocumentvault.backend.config.properties.StorageProperties;
import com.aidocumentvault.backend.processing.chunking.config.ChunkingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties({
    SecurityProperties.class,
    StorageProperties.class,
    OcrProperties.class,
    ChunkingProperties.class,
    AiChatProperties.class,
    AiEmbeddingProperties.class,
    AiOpenAiProperties.class,
    AiHybridSearchProperties.class,
    AiChatCompletionProperties.class
})
public class AiDocumentVaultBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDocumentVaultBackendApplication.class, args);
    }
}
