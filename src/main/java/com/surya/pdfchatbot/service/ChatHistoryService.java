package com.surya.pdfchatbot.service;

import com.surya.pdfchatbot.model.dto.ChatHistoryResponse;
import com.surya.pdfchatbot.model.entity.AppUser;
import com.surya.pdfchatbot.model.entity.ChatMessage;
import com.surya.pdfchatbot.repository.ChatMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatHistoryService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public ChatHistoryResponse getHistory(AppUser user, int page, int size) {
        if (user == null) {
            throw new IllegalArgumentException("User is required to fetch chat history");
        }

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ChatMessage> messages = chatMessageRepository.findByUserOrderByCreatedAtDesc(user, pageRequest);

        return new ChatHistoryResponse(
                messages.getContent().stream()
                        .map(this::toHistoryItem)
                        .toList(),
                messages.getNumber(),
                messages.getSize(),
                messages.getTotalElements()
        );
    }

    private ChatHistoryResponse.ChatHistoryItem toHistoryItem(ChatMessage message) {
        return new ChatHistoryResponse.ChatHistoryItem(
                message.getId(),
                message.getDocumentId(),
                message.getQuestion(),
                message.getAnswer(),
                message.getResponseFormat().name(),
                message.getCreatedAt()
        );
    }
}
