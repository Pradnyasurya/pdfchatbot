package com.surya.pdfchatbot.service;

import com.surya.pdfchatbot.exception.DocumentProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PdfProcessingService.class);

    /**
     * Extract text from PDF file
     */
    public PdfContent extractText(File pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            
            int pageCount = document.getNumberOfPages();
            List<PageContent> pages = new ArrayList<>();
            
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                pages.add(new PageContent(i, pageText));
            }
            
            log.info("Extracted text from {} pages", pageCount);
            
            return new PdfContent(pageCount, pages);
            
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to extract text from PDF", e);
        }
    }

    /**
     * Extract text from PDF with path
     */
    public PdfContent extractText(String filePath) {
        return extractText(new File(filePath));
    }

    /**
     * DTO for PDF content
     */
    public record PdfContent(int pageCount, List<PageContent> pages) {
        public String getAllText() {
            StringBuilder sb = new StringBuilder();
            for (PageContent page : pages) {
                sb.append(page.text()).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * DTO for page content
     */
    public record PageContent(int pageNumber, String text) {}
}
