package com.aidocumentvault.backend.ai.embedding;

import java.util.Arrays;

public record EmbeddingVector(float[] values) {

    public EmbeddingVector {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("embedding vector must not be empty");
        }

        values = Arrays.copyOf(values, values.length);
    }

    @Override
    public float[] values() {
        return Arrays.copyOf(values, values.length);
    }

    public int dimensions() {
        return values.length;
    }
}
