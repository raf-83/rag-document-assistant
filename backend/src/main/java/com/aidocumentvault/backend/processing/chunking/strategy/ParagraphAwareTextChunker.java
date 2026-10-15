package com.aidocumentvault.backend.processing.chunking.strategy;

import com.aidocumentvault.backend.processing.chunking.config.ChunkingProperties;
import com.aidocumentvault.backend.processing.chunking.dto.ChunkCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ParagraphAwareTextChunker implements TextChunker {

    private static final int ESTIMATED_CHARS_PER_TOKEN = 4;

    private final ChunkingProperties chunkingProperties;

    @Override
    public List<ChunkCandidate> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int maxChunkChars = chunkingProperties.maxChunkChars();
        int overlapChars = chunkingProperties.overlapChars();

        validateConfiguration(maxChunkChars, overlapChars);

        List<ChunkCandidate> chunks = new ArrayList<>();

        int chunkIndex = 0;
        int currentStart = 0;

        while (currentStart < text.length()) {
            int currentEnd = calculateChunkEnd(text, currentStart, maxChunkChars);

            int actualStart = findActualStart(text, currentStart, currentEnd);
            int actualEnd = findActualEnd(text, currentStart, currentEnd);

            if (actualStart < actualEnd) {
                String chunkText = text.substring(actualStart, actualEnd);

                chunks.add(new ChunkCandidate(
                        chunkIndex,
                        chunkText,
                        actualStart,
                        actualEnd,
                        chunkText.length(),
                        estimateTokenCount(chunkText),
                        chunkIndex == 0 ? 0 : overlapChars,
                        currentEnd < text.length() ? overlapChars : 0
                ));

                chunkIndex++;
            }

            if (currentEnd >= text.length()) {
                break;
            }

            currentStart = Math.max(currentEnd - overlapChars, currentStart + 1);
        }

        return chunks;
    }

    private int calculateChunkEnd(String text, int currentStart, int maxChunkChars) {
        int maxEnd = Math.min(currentStart + maxChunkChars, text.length());

        if (maxEnd == text.length()) {
            return maxEnd;
        }

        int paragraphBreak = text.lastIndexOf("\n\n", maxEnd);

        if (paragraphBreak > currentStart) {
            return paragraphBreak;
        }

        int lineBreak = text.lastIndexOf('\n', maxEnd);

        if (lineBreak > currentStart) {
            return lineBreak;
        }

        int sentenceEnd = findLastSentenceEnd(text, currentStart, maxEnd);

        if (sentenceEnd > currentStart) {
            return sentenceEnd;
        }

        int space = text.lastIndexOf(' ', maxEnd);

        if (space > currentStart) {
            return space;
        }

        return maxEnd;
    }

    private int findLastSentenceEnd(String text, int currentStart, int maxEnd) {
        int dot = text.lastIndexOf('.', maxEnd);
        int questionMark = text.lastIndexOf('?', maxEnd);
        int exclamationMark = text.lastIndexOf('!', maxEnd);

        int sentenceEnd = Math.max(dot, Math.max(questionMark, exclamationMark));

        if (sentenceEnd <= currentStart) {
            return -1;
        }

        return sentenceEnd + 1;
    }

    private int findActualStart(String text, int currentStart, int currentEnd) {
        int index = currentStart;

        while (index < currentEnd && Character.isWhitespace(text.charAt(index))) {
            index++;
        }

        return index;
    }

    private int findActualEnd(String text, int currentStart, int currentEnd) {
        int index = currentEnd;

        while (index > currentStart && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }

        return index;
    }

    private int estimateTokenCount(String text) {
        return Math.max(1, (int) Math.ceil((double) text.length() / ESTIMATED_CHARS_PER_TOKEN));
    }

    private void validateConfiguration(int maxChunkChars, int overlapChars) {
        if (maxChunkChars <= 0) {
            throw new IllegalStateException("maxChunkChars must be greater than 0");
        }

        if (overlapChars < 0) {
            throw new IllegalStateException("overlapChars must not be negative");
        }

        if (overlapChars >= maxChunkChars) {
            throw new IllegalStateException("overlapChars must be smaller than maxChunkChars");
        }
    }
}
