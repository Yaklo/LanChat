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
        // 从WebSocket连接的URI中提取客户端信息
        String clientInfo = extractClientInfo(session);
        // 将客户端信息存储到session属性中
        session.getAttributes().put("clientInfo", clientInfo);
        sessions.put(session.getId(), session);
        System.out.println("WebSocket连接建立: " + clientInfo + ", Session: " + session.getId());

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
        String uniqueId = (String) payload.get("uniqueId");
        String userName = (String) payload.get("userName");
        String content = (String) payload.get("content");
        String messageType = (String) payload.get("messageType");
        Long fileId = payload.get("fileId") != null ? Long.valueOf(payload.get("fileId").toString()) : null;

        // 添加调试信息
        System.out.println("=== 接收到消息 ===");
        System.out.println("用户IP: " + userIp);
        System.out.println("用户唯一ID: " + uniqueId);
        System.out.println("用户名: " + userName);
        System.out.println("消息内容: " + content);
        System.out.println("Session ID: " + session.getId());
        System.out.println("==================");

        ChatMessage savedMessage = chatService.saveMessage(userIp, uniqueId, userName, content,
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

    private String extractClientInfo(WebSocketSession session) {
        try {
            // 尝试从WebSocket连接的URI中提取客户端信息
            String uri = session.getUri().toString();
            System.out.println("WebSocket连接URI: " + uri);

            // 从URI参数中提取客户端信息（如果有的话）
            if (uri.contains("client=")) {
                String[] params = uri.split("&");
                for (String param : params) {
                    if (param.startsWith("client=")) {
                        return param.substring(7); // 去掉"client="前缀
                    }
                }
            }

            // 如果URI中没有客户端信息，使用session ID + 时间戳作为唯一标识
            return "Client_" + session.getId().substring(0, 8) + "_" + System.currentTimeMillis();
        } catch (Exception e) {
            System.err.println("提取客户端信息失败: " + e.getMessage());
            // 降级处理：使用session ID作为客户端标识
            return "Client_" + session.getId().substring(0, 8);
        }
    }

    private String getClientIp(WebSocketSession session) {
        // 从session属性中获取客户端信息
        Map<String, Object> attributes = session.getAttributes();
        return (String) attributes.get("clientInfo");
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