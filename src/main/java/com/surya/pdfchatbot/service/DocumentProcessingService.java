package com.surya.pdfchatbot.service;

import com.surya.pdfchatbot.exception.DocumentNotFoundException;
import com.surya.pdfchatbot.model.entity.Document;
import com.surya.pdfchatbot.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for async document processing.
 * Separated from DocumentService to ensure @Async works correctly via Spring proxy.
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentRepository documentRepository;
    private final PdfProcessingService pdfProcessingService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    public DocumentProcessingService(
            DocumentRepository documentRepository,
            PdfProcessingService pdfProcessingService,
            ChunkingService chunkingService,
            EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.pdfProcessingService = pdfProcessingService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
    }

    /**
     * Process document asynchronously - this MUST be called from another bean for @Async to work
     */
    @Async
    public void processDocumentAsync(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        try {
            log.info("Starting async processing for document: {}", documentId);

            // Extract text from PDF
            PdfProcessingService.PdfContent pdfContent = pdfProcessingService.extractText(document.getFilePath());
            document.setPageCount(pdfContent.pageCount());

            // Chunk the text
            List<ChunkingService.TextChunk> chunks = chunkingService.chunkDocument(pdfContent);
            document.setChunkCount(chunks.size());

            // Generate and store embeddings
            embeddingService.storeDocumentChunks(documentId, chunks);

            // Mark as ready
            document.setProcessingStatus(Document.ProcessingStatus.READY);
            document.setErrorMessage(null);

            log.info("Successfully processed document: {} with {} pages and {} chunks",
                    documentId, pdfContent.pageCount(), chunks.size());

        } catch (Exception e) {
            log.error("Failed to process document: {}", documentId, e);
            document.setProcessingStatus(Document.ProcessingStatus.FAILED);
            document.setErrorMessage("Processing failed: " + e.getMessage());
        } finally {
            documentRepository.save(document);
        }
    }
}
