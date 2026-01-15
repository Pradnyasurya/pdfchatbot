package com.surya.pdfchatbot.service;

import com.surya.pdfchatbot.exception.DocumentNotFoundException;
import com.surya.pdfchatbot.exception.InvalidDocumentException;
import com.surya.pdfchatbot.model.dto.DocumentListResponse;
import com.surya.pdfchatbot.model.dto.DocumentStatusResponse;
import com.surya.pdfchatbot.model.dto.DocumentUploadResponse;
import com.surya.pdfchatbot.model.entity.Document;
import com.surya.pdfchatbot.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final PdfProcessingService pdfProcessingService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    public DocumentService(
            DocumentRepository documentRepository,
            FileStorageService fileStorageService,
            PdfProcessingService pdfProcessingService,
            ChunkingService chunkingService,
            EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.pdfProcessingService = pdfProcessingService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
    }

    /**
     * Upload and process PDF document
     */
    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file) {
        // Validate file
        validateFile(file);

        // Store file
        FileStorageService.StoredFileInfo fileInfo = fileStorageService.store(file);

        // Create document entity
        Document document = new Document();
        document.setId(fileInfo.documentId());
        document.setFilename(fileInfo.originalFilename());
        document.setOriginalFilename(fileInfo.originalFilename());
        document.setFilePath(fileInfo.filePath());
        document.setFileSize(fileInfo.fileSize());
        document.setUploadDate(LocalDateTime.now());
        document.setProcessingStatus(Document.ProcessingStatus.PROCESSING);

        documentRepository.save(document);

        // Process document asynchronously
        processDocumentAsync(document.getId());

        return new DocumentUploadResponse(
                document.getId(),
                document.getFilename(),
                document.getProcessingStatus().toString(),
                "Document uploaded successfully and is being processed",
                document.getUploadDate()
        );
    }

    /**
     * Process document asynchronously
     */
    @Async
    public void processDocumentAsync(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        try {
            log.info("Starting processing for document: {}", documentId);

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

    /**
     * Get document status
     */
    public DocumentStatusResponse getDocumentStatus(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        return new DocumentStatusResponse(
                document.getId(),
                document.getFilename(),
                document.getProcessingStatus().toString(),
                document.getPageCount(),
                document.getChunkCount(),
                document.getUploadDate(),
                getStatusMessage(document),
                document.getErrorMessage()
        );
    }

    /**
     * List all documents
     */
    public DocumentListResponse listDocuments() {
        List<Document> documents = documentRepository.findAllByOrderByUploadDateDesc();
        
        List<DocumentListResponse.DocumentInfo> documentInfos = documents.stream()
                .map(doc -> new DocumentListResponse.DocumentInfo(
                        doc.getId(),
                        doc.getFilename(),
                        doc.getProcessingStatus().toString(),
                        doc.getPageCount(),
                        doc.getFileSize(),
                        doc.getUploadDate().format(DATE_FORMATTER)
                ))
                .toList();

        return new DocumentListResponse(documentInfos);
    }

    /**
     * Delete document
     */
    @Transactional
    public void deleteDocument(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        // Delete file from storage
        fileStorageService.delete(documentId);

        // Delete embeddings from vector store
        embeddingService.deleteDocumentChunks(documentId);

        // Delete from database
        documentRepository.delete(document);

        log.info("Deleted document: {}", documentId);
    }

    /**
     * Validate uploaded file
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidDocumentException("File is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new InvalidDocumentException("Invalid filename");
        }

        if (!filename.toLowerCase().endsWith(".pdf")) {
            throw new InvalidDocumentException("Only PDF files are allowed");
        }

        long maxSize = 50 * 1024 * 1024; // 50 MB
        if (file.getSize() > maxSize) {
            throw new InvalidDocumentException("File size exceeds maximum limit of 50MB");
        }
    }

    /**
     * Get human-readable status message
     */
    private String getStatusMessage(Document document) {
        return switch (document.getProcessingStatus()) {
            case UPLOADING -> "Document is being uploaded";
            case PROCESSING -> "Document is being processed";
            case READY -> "Document is ready for querying";
            case FAILED -> "Document processing failed";
        };
    }
}
