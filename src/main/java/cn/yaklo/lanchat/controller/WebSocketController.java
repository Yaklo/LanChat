package cn.yaklo.lanchat.controller;

import cn.yaklo.lanchat.dto.ChatMessageDto;
import cn.yaklo.lanchat.entity.ChatFile;
import cn.yaklo.lanchat.entity.ChatMessage;
import cn.yaklo.lanchat.service.ChatService;
import cn.yaklo.lanchat.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class WebSocketController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private FileService fileService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload Map<String, Object> message) {
        try {
            String userIp = (String) message.get("userIp");
            String userName = (String) message.get("userName");
            String content = (String) message.get("content");
            String messageType = (String) message.get("messageType");
            Long fileId = message.get("fileId") != null ? Long.valueOf(message.get("fileId").toString()) : null;

            ChatMessage savedMessage = chatService.saveMessage(userIp, userName, content,
                    ChatMessage.MessageType.valueOf(messageType), fileId);

            ChatFile file = null;
            if (fileId != null) {
                file = fileService.getFileById(fileId);
            }

            ChatMessageDto messageDto = ChatMessageDto.fromEntity(savedMessage, file);

            messagingTemplate.convertAndSend("/topic/public", messageDto);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @MessageMapping("/chat.recallMessage")
    public void recallMessage(@Payload Map<String, Object> payload) {
        try {
            Long messageId = Long.valueOf(payload.get("messageId").toString());
            String userIp = (String) payload.get("userIp");

            ChatMessage recalledMessage = chatService.recallMessage(messageId, userIp);
            if (recalledMessage != null) {
                ChatMessageDto messageDto = ChatMessageDto.fromEntity(recalledMessage, null);
                messagingTemplate.convertAndSend("/topic/public", messageDto);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}