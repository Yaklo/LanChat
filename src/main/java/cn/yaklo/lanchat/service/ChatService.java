package cn.yaklo.lanchat.service;

import cn.yaklo.lanchat.entity.ChatMessage;
import cn.yaklo.lanchat.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    public ChatMessage saveMessage(String userIp, String userName, String content,
                                 ChatMessage.MessageType messageType, Long fileId) {
        ChatMessage message = new ChatMessage(userIp, userName, content, messageType);
        if (fileId != null) {
            message.setFileId(fileId);
        }
        return chatMessageRepository.save(message);
    }

    public List<ChatMessage> getRecentMessages(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return chatMessageRepository.findRecentMessages(pageable);
    }

    public List<ChatMessage> getMessagesBefore(LocalDateTime timestamp, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return chatMessageRepository.findMessagesBefore(timestamp, pageable);
    }

    public List<ChatMessage> getMessagesByUser(String userIp) {
        return chatMessageRepository.findByUserIpOrderByTimestampDesc(userIp);
    }

    public ChatMessage recallMessage(Long messageId, String userIp) {
        ChatMessage message = chatMessageRepository.findActiveMessageByIdAndUserIp(messageId, userIp);
        if (message != null) {
            message.recall();
            return chatMessageRepository.save(message);
        }
        return null;
    }

    public List<ChatMessage> getAllActiveMessages() {
        return chatMessageRepository.findAllActiveMessagesOrderByTimestamp();
    }

    public long getTotalMessageCount() {
        return chatMessageRepository.countByRecalledFalse();
    }

    public ChatMessage getMessageById(Long messageId) {
        return chatMessageRepository.findById(messageId).orElse(null);
    }
}