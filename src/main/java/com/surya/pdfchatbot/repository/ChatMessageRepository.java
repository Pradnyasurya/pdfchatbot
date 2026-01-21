package com.surya.pdfchatbot.repository;

import com.surya.pdfchatbot.model.entity.AppUser;
import com.surya.pdfchatbot.model.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    Page<ChatMessage> findByUserOrderByCreatedAtDesc(AppUser user, Pageable pageable);
}
