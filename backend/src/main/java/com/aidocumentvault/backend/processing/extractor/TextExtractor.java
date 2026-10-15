package com.aidocumentvault.backend.processing.extractor;

import com.aidocumentvault.backend.processing.dto.TextExtractionResult;
import com.aidocumentvault.backend.storage.entity.StoredFile;
import java.nio.file.Path;

public interface TextExtractor {

    boolean supports(StoredFile storedFile);

    TextExtractionResult extract(Path filePath);
}
