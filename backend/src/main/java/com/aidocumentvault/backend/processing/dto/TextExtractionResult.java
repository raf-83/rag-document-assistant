package com.aidocumentvault.backend.processing.dto;

import com.aidocumentvault.backend.document.entity.ExtractionMethod;

public record TextExtractionResult(
        String text,
        ExtractionMethod extractionMethod,
        Integer pageCount
) {
}
