package com.surya.pdfchatbot.service;

import com.surya.pdfchatbot.config.RagConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]\\s+");

    private final RagConfig ragConfig;

    public ChunkingService(RagConfig ragConfig) {
        this.ragConfig = ragConfig;
    }

    /**
     * Chunk text with overlap, trying to split on sentence boundaries
     */
    public List<TextChunk> chunkText(String text, int pageNumber) {
        List<TextChunk> chunks = new ArrayList<>();
        
        if (text == null || text.isBlank()) {
            return chunks;
        }

        int chunkSize = ragConfig.getChunkSize();
        int overlap = ragConfig.getChunkOverlap();
        
        int position = 0;
        int chunkIndex = 0;

        while (position < text.length()) {
            int endPosition = Math.min(position + chunkSize, text.length());
            
            // Try to find a sentence boundary near the end position
            if (endPosition < text.length()) {
                String substring = text.substring(position, endPosition);
                int lastSentenceEnd = findLastSentenceBoundary(substring);
                
                if (lastSentenceEnd > overlap) {
                    endPosition = position + lastSentenceEnd;
                }
            }

            String chunkText = text.substring(position, endPosition).trim();
            
            if (!chunkText.isBlank()) {
                chunks.add(new TextChunk(chunkIndex, pageNumber, chunkText, position, endPosition));
                chunkIndex++;
            }

            // Move position forward, accounting for overlap
            position = endPosition - overlap;
            
            // Ensure we're making progress
            if (position <= endPosition - chunkSize + overlap) {
                position = endPosition - overlap;
            }
        }

        log.debug("Created {} chunks from page {}", chunks.size(), pageNumber);
        return chunks;
    }

    /**
     * Chunk entire document page by page
     */
    public List<TextChunk> chunkDocument(PdfProcessingService.PdfContent pdfContent) {
        List<TextChunk> allChunks = new ArrayList<>();
        
        for (PdfProcessingService.PageContent page : pdfContent.pages()) {
            List<TextChunk> pageChunks = chunkText(page.text(), page.pageNumber());
            allChunks.addAll(pageChunks);
        }

        log.info("Created {} total chunks from {} pages", allChunks.size(), pdfContent.pageCount());
        return allChunks;
    }

    /**
     * Find the last sentence boundary in the text
     */
    private int findLastSentenceBoundary(String text) {
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        int lastEnd = -1;
        
        while (matcher.find()) {
            lastEnd = matcher.end();
        }
        
        return lastEnd;
    }

    /**
     * DTO for text chunk with metadata
     */
    public record TextChunk(
            int chunkIndex,
            int pageNumber,
            String content,
            int startPosition,
            int endPosition
    ) {
        public int getLength() {
            return content.length();
        }
    }
}
