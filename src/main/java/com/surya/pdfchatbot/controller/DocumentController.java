package com.surya.pdfchatbot.controller;

import com.surya.pdfchatbot.model.dto.DocumentListResponse;
import com.surya.pdfchatbot.model.dto.DocumentStatusResponse;
import com.surya.pdfchatbot.model.dto.DocumentUploadResponse;
import com.surya.pdfchatbot.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Upload a PDF document
     */
    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) {
        log.info("Received document upload request: {}", file.getOriginalFilename());
        
        DocumentUploadResponse response = documentService.uploadDocument(file);
        
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Get document processing status
     */
    @GetMapping("/{documentId}/status")
    public ResponseEntity<DocumentStatusResponse> getDocumentStatus(
            @PathVariable String documentId) {
        log.info("Received status request for document: {}", documentId);
        
        DocumentStatusResponse response = documentService.getDocumentStatus(documentId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * List all documents
     */
    @GetMapping
    public ResponseEntity<DocumentListResponse> listDocuments() {
        log.info("Received list documents request");
        
        DocumentListResponse response = documentService.listDocuments();
        
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a document
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Map<String, String>> deleteDocument(
            @PathVariable String documentId) {
        log.info("Received delete request for document: {}", documentId);
        
        documentService.deleteDocument(documentId);
        
        return ResponseEntity.ok(Map.of(
                "message", "Document deleted successfully",
                "documentId", documentId
        ));
    }
}
