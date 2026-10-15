package com.aidocumentvault.backend.processing.chunking.strategy;

import com.aidocumentvault.backend.processing.chunking.config.ChunkingProperties;
import com.aidocumentvault.backend.processing.chunking.dto.ChunkCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParagraphAwareTextChunkerTest {

    @Test
    void shouldReturnEmptyListWhenTextIsNull() {
        // given
        ParagraphAwareTextChunker chunker = createChunker(100, 20, 30);

        // when
        List<ChunkCandidate> chunks = chunker.chunk(null);

        // then
        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenTextIsBlank() {
        // given
        ParagraphAwareTextChunker chunker = createChunker(100, 20, 30);

        // when
        List<ChunkCandidate> chunks = chunker.chunk("     \n\n   ");

        // then
        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldCreateSingleChunkWhenTextIsShorterThanMaxChunkSize() {
        // given
        ParagraphAwareTextChunker chunker = createChunker(100, 20, 30);
        String text = "To jest krótki tekst testowy.";

        // when
        List<ChunkCandidate> chunks = chunker.chunk(text);

        // then
        assertThat(chunks).hasSize(1);

        ChunkCandidate chunk = chunks.get(0);

        assertThat(chunk.chunkIndex()).isZero();
        assertThat(chunk.text()).isEqualTo(text);
        assertThat(chunk.startCharIndex()).isZero();
        assertThat(chunk.endCharIndex()).isEqualTo(text.length());
        assertThat(chunk.characterCount()).isEqualTo(text.length());
        assertThat(chunk.overlapPreviousChars()).isZero();
        assertThat(chunk.overlapNextChars()).isZero();
        assertThat(chunk.estimatedTokenCount()).isGreaterThan(0);
    }

    @Test
    void shouldCreateMultipleChunksForLongText() {
        // given
        ParagraphAwareTextChunker chunker = createChunker(80, 20, 30);
        String text = """
                Pierwszy akapit ma trochę tekstu i powinien wejść do pierwszego chunka.

                Drugi akapit też ma trochę tekstu i powinien zostać sensownie podzielony.

                Trzeci akapit istnieje po to, żeby wymusić utworzenie kolejnego chunka.
                """;

        // when
        List<ChunkCandidate> chunks = chunker.chunk(text);

        // then
        assertThat(chunks).hasSizeGreaterThan(1);

        assertThat(chunks)
                .extracting(ChunkCandidate::chunkIndex)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.range(0, chunks.size())
                                .boxed()
                                .toList()
                );

        assertThat(chunks)
                .allSatisfy(chunk -> {
                    assertThat(chunk.text()).isNotBlank();
                    assertThat(chunk.characterCount()).isEqualTo(chunk.text().length());
                    assertThat(chunk.endCharIndex()).isGreaterThan(chunk.startCharIndex());
                    assertThat(chunk.estimatedTokenCount()).isGreaterThan(0);
                });
    }

    @Test
    void shouldUseOverlapBetweenChunks() {
        // given
        ParagraphAwareTextChunker chunker = createChunker(80, 20, 30);
        String text = """
                Pierwszy akapit ma trochę tekstu i powinien wejść do pierwszego chunka.

                Drugi akapit też ma trochę tekstu i powinien zostać sensownie podzielony.

                Trzeci akapit istnieje po to, żeby wymusić utworzenie kolejnego chunka.
                """;

        // when
        List<ChunkCandidate> chunks = chunker.chunk(text);

        // then
        assertThat(chunks).hasSizeGreaterThan(1);

        ChunkCandidate firstChunk = chunks.get(0);
        ChunkCandidate secondChunk = chunks.get(1);

        assertThat(secondChunk.startCharIndex()).isLessThan(firstChunk.endCharIndex());
        assertThat(secondChunk.overlapPreviousChars()).isEqualTo(20);
        assertThat(firstChunk.overlapNextChars()).isEqualTo(20);
    }

    @Test
    void shouldThrowExceptionWhenMaxChunkCharsIsNotPositive() {
        // given
        ParagraphAwareTextChunker chunker = createChunker(0, 20, 30);

        // when / then
        assertThatThrownBy(() -> chunker.chunk("Jakiś tekst testowy."))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maxChunkChars");
    }

    @Test
    void shouldThrowExceptionWhenOverlapIsEqualToMaxChunkChars() {
        // given
        ParagraphAwareTextChunker chunker = createChunker(100, 100, 30);

        // when / then
        assertThatThrownBy(() -> chunker.chunk("Jakiś tekst testowy."))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overlapChars");
    }

    private ParagraphAwareTextChunker createChunker(
            int maxChunkChars,
            int overlapChars,
            int minChunkChars
    ) {
        ChunkingProperties properties = new ChunkingProperties(
                maxChunkChars,
                overlapChars,
                minChunkChars,
                "PARAGRAPH_AWARE_V1"
        );

        return new ParagraphAwareTextChunker(properties);
    }
}
