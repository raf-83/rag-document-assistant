package com.aidocumentvault.backend.processing.sanitizer;

import org.springframework.stereotype.Component;

@Component
public class BasicTextSanitizer implements TextSanitizer {

    @Override
    public String sanitize(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("rawText cannot be null or blank");
        }

        return rawText
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u2007', ' ')
            .replace('\u2009', ' ')
            .replace("\u200B", "")
            .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
            .replaceAll("[\\h&&[^\\n]]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }
}
