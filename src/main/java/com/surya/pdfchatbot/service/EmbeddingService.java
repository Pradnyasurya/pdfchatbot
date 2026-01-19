package com.surya.pdfchatbot.service;

import com.surya.pdfchatbot.exception.DocumentProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final VectorStore vectorStore;

    public EmbeddingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Generate embeddings and store chunks in vector database
     */
    public void storeDocumentChunks(String documentId, List<ChunkingService.TextChunk> chunks) {
        try {
            int batchSize = 10; // Process 10 chunks at a time for better memory management
            int totalChunks = chunks.size();
            
            log.info("Starting to store {} chunks for document {} in batches of {}", totalChunks, documentId, batchSize);
            
            for (int i = 0; i < totalChunks; i += batchSize) {
                int endIndex = Math.min(i + batchSize, totalChunks);
                List<ChunkingService.TextChunk> batch = chunks.subList(i, endIndex);
                
                List<Document> documents = new ArrayList<>();
                for (ChunkingService.TextChunk chunk : batch) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("documentId", documentId);
                    metadata.put("pageNumber", chunk.pageNumber());
                    metadata.put("chunkIndex", chunk.chunkIndex());
                    metadata.put("startPosition", chunk.startPosition());
                    metadata.put("endPosition", chunk.endPosition());

                    Document document = new Document(
                            chunk.content(),
                            metadata
                    );
                    
                    documents.add(document);
                }

                // Store batch in vector store
                vectorStore.add(documents);
                log.info("Stored batch {}/{} ({} chunks) for document {}", 
                        (i/batchSize) + 1, (totalChunks + batchSize - 1)/batchSize, documents.size(), documentId);
                
                // Log first chunk metadata for debugging
                if (i == 0 && !documents.isEmpty()) {
                    log.debug("Sample chunk metadata: {}", documents.get(0).getMetadata());
                }
                
                // Allow GC to clean up
                documents.clear();
            }

            log.info("Successfully stored {} chunks for document {} in vector store", totalChunks, documentId);

        } catch (Exception e) {
            throw new DocumentProcessingException("Failed to store embeddings for document: " + documentId, e);
        }
    }

    /**
     * Delete all chunks for a document from vector store
     */
    public void deleteDocumentChunks(String documentId) {
        try {
            log.info("Deleting chunks for document {} from vector store", documentId);
            
            // Create filter to find all chunks belonging to this document
            var filterExpression = new FilterExpressionBuilder()
                    .eq("documentId", documentId)
                    .build();
            
            // Search for all chunks with this documentId to get their IDs
            SearchRequest searchRequest = SearchRequest.builder()
                    .query("*")
                    .topK(1000) // Get up to 1000 chunks per document
                    .similarityThreshold(0.0) // Accept all matches
                    .filterExpression(filterExpression)
                    .build();
            
            List<Document> documentsToDelete = vectorStore.similaritySearch(searchRequest);
            
            if (documentsToDelete.isEmpty()) {
                log.info("No chunks found for document {} in vector store", documentId);
                return;
            }
            
            // Extract document IDs and delete them
            List<String> idsToDelete = documentsToDelete.stream()
                    .map(Document::getId)
                    .filter(id -> id != null && !id.isEmpty())
                    .toList();
            
            if (!idsToDelete.isEmpty()) {
                vectorStore.delete(idsToDelete);
                log.info("Successfully deleted {} chunks for document {} from vector store", 
                        idsToDelete.size(), documentId);
            } else {
                log.warn("Found {} chunks but could not extract IDs for deletion for document {}", 
                        documentsToDelete.size(), documentId);
            }
            
        } catch (Exception e) {
            log.error("Failed to delete embeddings for document: {}", documentId, e);
            throw new DocumentProcessingException("Failed to delete document chunks from vector store: " + documentId, e);
        }
    }

    /**
     * Check if vector store is accessible
     */
    public boolean isVectorStoreHealthy() {
        try {
            // Simple health check - try to query the store
            vectorStore.similaritySearch("test");
            return true;
        } catch (Exception e) {
            log.error("Vector store health check failed", e);
            return false;
        }
    }
}
