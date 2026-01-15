package com.surya.pdfchatbot.repository;

import com.surya.pdfchatbot.model.entity.Document;
import com.surya.pdfchatbot.model.entity.Document.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {

    List<Document> findByProcessingStatus(ProcessingStatus status);

    Optional<Document> findByFilename(String filename);

    List<Document> findAllByOrderByUploadDateDesc();
}
