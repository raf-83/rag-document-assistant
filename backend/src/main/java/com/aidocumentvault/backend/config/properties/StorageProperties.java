package com.aidocumentvault.backend.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        Local local,
        Long maxFileSizeBytes,
        List<String> allowedExtensions,
        List<String> allowedMimeTypes,
        Integer maxPdfPages
) {

    public record Local(
            String basePath
    ) {
    }
}
