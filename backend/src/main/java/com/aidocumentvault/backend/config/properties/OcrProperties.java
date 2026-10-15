package com.aidocumentvault.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ocr.tesseract")
public record OcrProperties(
        String dataPath,
        String language
) {
}
