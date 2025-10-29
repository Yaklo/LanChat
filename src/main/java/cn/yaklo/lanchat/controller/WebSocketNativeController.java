package cn.yaklo.lanchat.controller;

import cn.yaklo.lanchat.dto.ChatMessageDto;
import cn.yaklo.lanchat.entity.ChatFile;
import cn.yaklo.lanchat.entity.ChatMessage;
import cn.yaklo.lanchat.service.ChatService;
import cn.yaklo.lanchat.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketNativeController extends TextWebSocketHandler {

    @Autowired
    private ChatService chatService;

    @Autowired
    private FileService fileService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String clientIp = getClientIp(session);
        sessions.put(session.getId(), session);
        System.out.println("WebSocket连接建立: " + clientIp + ", Session: " + session.getId());

        // 发送最近的消息给新连接的客户端
        List<ChatMessage> recentMessages = chatService.getRecentMessages(30);
        for (ChatMessage message : recentMessages) {
            ChatFile file = null;
            if (message.getFileId() != null) {
                file = fileService.getFileById(message.getFileId());
            }
            ChatMessageDto messageDto = ChatMessageDto.fromEntity(message, file);
            sendMessage(session, messageDto);
        }

        // 广播在线用户数更新
        broadcastOnlineUserCount();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        System.out.println("WebSocket连接关闭: " + session.getId());

        // 广播在线用户数更新
        broadcastOnlineUserCount();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");

            if ("sendMessage".equals(type)) {
                handleSendMessage(session, payload);
            } else if ("recallMessage".equals(type)) {
                handleRecallMessage(session, payload);
            }
        } catch (Exception e) {
            System.err.println("处理WebSocket消息错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSendMessage(WebSocketSession session, Map<String, Object> payload) throws Exception {
        String userIp = (String) payload.get("userIp");
        String userName = (String) payload.get("userName");
        String content = (String) payload.get("content");
        String messageType = (String) payload.get("messageType");
        Long fileId = payload.get("fileId") != null ? Long.valueOf(payload.get("fileId").toString()) : null;

        ChatMessage savedMessage = chatService.saveMessage(userIp, userName, content,
                ChatMessage.MessageType.valueOf(messageType), fileId);

        ChatFile file = null;
        if (fileId != null) {
            file = fileService.getFileById(fileId);
        }

        ChatMessageDto messageDto = ChatMessageDto.fromEntity(savedMessage, file);

        // 广播消息给所有连接的客户端
        broadcastMessage(messageDto);
    }

    private void handleRecallMessage(WebSocketSession session, Map<String, Object> payload) throws Exception {
        Long messageId = Long.valueOf(payload.get("messageId").toString());
        String userIp = (String) payload.get("userIp");

        ChatMessage recalledMessage = chatService.recallMessage(messageId, userIp);
        if (recalledMessage != null) {
            ChatMessageDto messageDto = ChatMessageDto.fromEntity(recalledMessage, null);
            broadcastMessage(messageDto);
        }
    }

    private void sendMessage(WebSocketSession session, ChatMessageDto message) throws Exception {
        if (session.isOpen()) {
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
        }
    }

    private void broadcastMessage(ChatMessageDto message) throws Exception {
        String jsonMessage = objectMapper.writeValueAsString(message);
        TextMessage textMessage = new TextMessage(jsonMessage);

        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (Exception e) {
                    System.err.println("发送消息失败: " + e.getMessage());
                }
            }
        }
    }

    private String getClientIp(WebSocketSession session) {
        // 从session属性中获取IP地址
        Map<String, Object> attributes = session.getAttributes();
        return (String) attributes.get("clientIp");
    }

    public int getConnectedCount() {
        return sessions.size();
    }

    private void broadcastOnlineUserCount() throws Exception {
        Map<String, Object> userCountMessage = new HashMap<>();
        userCountMessage.put("type", "userCount");
        userCountMessage.put("count", sessions.size());

        String jsonMessage = objectMapper.writeValueAsString(userCountMessage);
        TextMessage textMessage = new TextMessage(jsonMessage);

        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (Exception e) {
                    System.err.println("发送在线用户数失败: " + e.getMessage());
                }
            }
        }
    }
}