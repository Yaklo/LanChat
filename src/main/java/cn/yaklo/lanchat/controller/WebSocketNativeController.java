package cn.yaklo.lanchat.controller;

import cn.yaklo.lanchat.dto.ChatMessageDto;
import cn.yaklo.lanchat.entity.ChatFile;
import cn.yaklo.lanchat.entity.ChatMessage;
import cn.yaklo.lanchat.service.ChatService;
import cn.yaklo.lanchat.service.FileService;
import cn.yaklo.lanchat.util.IpUtil;
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

        // 向客户端推送用户信息更新消息
        sendUserInfoUpdate(session, clientInfo);

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

        System.out.println("✓ WebSocket连接建立: " + clientInfo);
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

    private void handleSendMessage(WebSocketSession session, Map<String, Object> payload) throws Exception {
        // 从WebSocket session中获取真实的客户端信息
        String clientIp = getClientIp(session);
        String clientName = getClientName(session);

        // 从payload中获取消息信息
        String content = (String) payload.get("content");
        String messageType = (String) payload.get("messageType");
        Long fileId = payload.get("fileId") != null ? Long.valueOf(payload.get("fileId").toString()) : null;

        // 简化的调试信息
        System.out.println("接收到消息: " + clientName + " (" + clientIp + ") - " + content);

        // 直接使用IP作为唯一标识，简化保存逻辑
        ChatMessage savedMessage = chatService.saveMessage(clientIp, clientIp, clientName, content,
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
            // 直接从握手拦截器获取的真实IP
            Map<String, Object> attributes = session.getAttributes();
            String realIp = (String) attributes.get("clientIp");

            if (realIp != null && !realIp.isEmpty() && !"unknown".equals(realIp)) {
                // 尝试从URI参数中获取用户名（如果有的话）
                String userName = "用户(" + realIp + ")";  // 默认用户名格式
                String uri = session.getUri().toString();

                if (uri != null && uri.contains("client=")) {
                    try {
                        int clientParamIndex = uri.indexOf("client=");
                        if (clientParamIndex >= 0) {
                            int paramEndIndex = uri.indexOf("&", clientParamIndex);
                            if (paramEndIndex == -1) {
                                paramEndIndex = uri.length();
                            }
                            String clientParam = uri.substring(clientParamIndex + 7, paramEndIndex);
                            String clientInfo = java.net.URLDecoder.decode(clientParam, "UTF-8");

                            // 简化解析：只获取用户名部分
                            String[] parts = clientInfo.split("\\|");
                            if (parts.length >= 3) {
                                userName = parts[2];  // 使用传入的用户名
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("URL参数解析失败，使用默认用户名: " + e.getMessage());
                    }
                }

                // 将简化的信息存储到session属性中
                session.getAttributes().put("clientIp", realIp);
                session.getAttributes().put("clientName", userName);
                // 不再存储clientUniqueId等复杂信息

                String clientInfo = realIp + "|" + userName;
                System.out.println("客户端信息: " + clientInfo);
                return clientInfo;
            }

            // 降级处理：使用默认值
            String fallbackIp = "127.0.0.1";
            String fallbackName = "未知用户";
            String fallbackInfo = fallbackIp + "|" + fallbackName;

            session.getAttributes().put("clientIp", fallbackIp);
            session.getAttributes().put("clientName", fallbackName);

            System.out.println("使用默认客户端信息: " + fallbackInfo);
            return fallbackInfo;

        } catch (Exception e) {
            System.err.println("提取客户端信息错误: " + e.getMessage());
            String emergencyIp = "127.0.0.1";
            String emergencyName = "紧急用户";
            String emergencyInfo = emergencyIp + "|" + emergencyName;

            session.getAttributes().put("clientIp", emergencyIp);
            session.getAttributes().put("clientName", emergencyName);

            return emergencyInfo;
        }
    }

    private String getClientIp(WebSocketSession session) {
        String clientIp = (String) session.getAttributes().get("clientIp");
        if (clientIp != null && !clientIp.isEmpty()) {
            return clientIp;
        }
        return "127.0.0.1";  // 默认IP
    }

    private String getClientUniqueId(WebSocketSession session) {
        // 简化：直接使用IP作为唯一标识，不再生成复杂的UniqueID
        return getClientIp(session);
    }

    private String getClientName(WebSocketSession session) {
        String clientName = (String) session.getAttributes().get("clientName");
        if (clientName != null && !clientName.isEmpty()) {
            return clientName;
        }
        return "未知用户";  // 默认用户名
    }

    /**
     * 向客户端发送用户信息更新
     */
    private void sendUserInfoUpdate(WebSocketSession session, String clientInfo) throws Exception {
        // 解析客户端信息
        String[] parts = clientInfo.split("\\|");
        if (parts.length >= 2) {
            String ip = parts[0];
            String name = parts[1];

            // 创建用户信息更新消息
            Map<String, Object> userInfoUpdate = new HashMap<>();
            userInfoUpdate.put("type", "userInfoUpdate");
            userInfoUpdate.put("ip", ip);
            userInfoUpdate.put("name", name);

            String jsonMessage = objectMapper.writeValueAsString(userInfoUpdate);
            TextMessage textMessage = new TextMessage(jsonMessage);

            if (session.isOpen()) {
                session.sendMessage(textMessage);
                System.out.println("已向客户端推送用户信息更新: IP=" + ip + ", Name=" + name);
            }
        }
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