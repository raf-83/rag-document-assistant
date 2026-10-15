package com.aidocumentvault.backend.ai.embedding;

import java.util.List;

public interface EmbeddingClient {

    List<EmbeddingVector> embed(List<String> texts);
}
