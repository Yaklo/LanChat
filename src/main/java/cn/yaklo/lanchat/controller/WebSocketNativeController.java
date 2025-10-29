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
        System.out.println("\n=== WebSocket连接建立详细信息 ===");
        System.out.println("Session ID: " + session.getId());
        System.out.println("Session URI: " + session.getUri());
        System.out.println("Remote Address: " + session.getRemoteAddress());

        // 从WebSocket连接的URI中提取客户端信息
        String clientInfo = extractClientInfo(session);

        // 将客户端信息存储到session属性中
        session.getAttributes().put("clientInfo", clientInfo);
        sessions.put(session.getId(), session);

        // 显示最终的session属性
        System.out.println("最终Session属性:");
        System.out.println("  clientInfo: " + session.getAttributes().get("clientInfo"));
        System.out.println("  clientIp: " + session.getAttributes().get("clientIp"));
        System.out.println("  clientUniqueId: " + session.getAttributes().get("clientUniqueId"));
        System.out.println("  clientName: " + session.getAttributes().get("clientName"));

        System.out.println("✓ WebSocket连接建立成功: " + clientInfo + ", Session: " + session.getId());
        System.out.println("=====================================\n");

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

    private void handleSendMessage(WebSocketSession session, Map<String, Object> payload) throws Exception {
        // 从WebSocket session中获取真实的客户端信息
        String sessionIp = getClientIp(session);
        String sessionUniqueId = getClientUniqueId(session);
        String sessionName = getClientName(session);

        // 从payload中获取消息信息
        String payloadIp = (String) payload.get("userIp");
        String payloadUniqueId = (String) payload.get("uniqueId");
        String payloadUserName = (String) payload.get("userName");
        String content = (String) payload.get("content");
        String messageType = (String) payload.get("messageType");
        Long fileId = payload.get("fileId") != null ? Long.valueOf(payload.get("fileId").toString()) : null;

        // 使用session中的真实信息，而不是payload中的信息
        String finalIp = sessionIp;
        String finalUniqueId = sessionUniqueId;
        String finalUserName = sessionName;

        // 添加详细的调试信息
        System.out.println("\n=== 接收到消息详细分析 ===");
        System.out.println("Session信息:");
        System.out.println("  Session ID: " + session.getId());
        System.out.println("  Session IP: " + sessionIp);
        System.out.println("  Session UniqueID: " + sessionUniqueId);
        System.out.println("  Session Name: " + sessionName);
        System.out.println("Payload信息:");
        System.out.println("  Payload IP: " + payloadIp);
        System.out.println("  Payload UniqueID: " + payloadUniqueId);
        System.out.println("  Payload UserName: " + payloadUserName);
        System.out.println("最终使用的信息:");
        System.out.println("  Final IP: " + finalIp);
        System.out.println("  Final UniqueID: " + finalUniqueId);
        System.out.println("  Final UserName: " + finalUserName);
        System.out.println("  消息内容: " + content);
        System.out.println("  消息类型: " + messageType);
        System.out.println("  文件ID: " + fileId);
        System.out.println("========================\n");

        // 验证必要信息
        if (finalIp.equals("unknown") || finalUniqueId.equals("unknown")) {
            System.err.println("警告：无法获取有效的客户端身份信息");
            // 可以选择拒绝消息或使用默认值
        }

        ChatMessage savedMessage = chatService.saveMessage(finalIp, finalUniqueId, finalUserName, content,
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
            String uri = session.getUri().toString();
            System.out.println("=== 开始提取客户端信息 ===");
            System.out.println("WebSocket连接URI: " + uri);
            System.out.println("Session ID: " + session.getId());

            // 优先从握手拦截器获取的IP信息
            Map<String, Object> attributes = session.getAttributes();
            String handshakeIp = (String) attributes.get("clientIp");
            if (handshakeIp != null && !handshakeIp.isEmpty() && !"unknown".equals(handshakeIp)) {
                System.out.println("✓ 使用握手拦截器设置的IP: " + handshakeIp);

                // 尝试从URI参数中获取完整的客户端信息（如果有的话）
                String finalUniqueId = null;
                String finalName = "未知用户";
                String payloadIp = null;

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
                            System.out.println("从URL参数提取的客户端信息: " + clientInfo);

                            // 解析客户端信息（格式：ip|uniqueId|name）
                            String[] parts = clientInfo.split("\\|");
                            if (parts.length >= 3) {
                                payloadIp = parts[0];
                                finalUniqueId = parts[1];
                                finalName = parts[2];
                                System.out.println("✓ 从URL解析成功:");
                                System.out.println("  Payload IP: " + payloadIp);
                                System.out.println("  UniqueID: " + finalUniqueId);
                                System.out.println("  Name: " + finalName);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("URL参数解析失败，使用默认值: " + e.getMessage());
                    }
                }

                // 智能IP选择：优先使用握手拦截器获取的真实IP
                String finalIp = handshakeIp;

                // 智能UniqueID处理：如果存在UniqueID，则使用现有的
                if (finalUniqueId == null || finalUniqueId.isEmpty() || finalUniqueId.startsWith("unknown_")) {
                    // 生成基于真实IP的临时UniqueID
                    String userAgent = session.getHandshakeHeaders().getFirst("User-Agent");
                    if (userAgent == null) {
                        userAgent = "Unknown";
                    }
                    finalUniqueId = cn.yaklo.lanchat.util.IpUtil.generateTemporarySessionId(finalIp, userAgent);
                    System.out.println("生成临时UniqueID: " + finalUniqueId);
                }

                // 将信息存储到session属性中
                session.getAttributes().put("clientIp", finalIp);
                session.getAttributes().put("clientUniqueId", finalUniqueId);
                session.getAttributes().put("clientName", finalName);
                session.getAttributes().put("payloadIp", payloadIp);

                String clientInfo = finalIp + "|" + finalUniqueId + "|" + finalName;
                System.out.println("最终客户端信息: " + clientInfo);
                System.out.println("========================");
                return clientInfo;
            }

            // 备用方案：尝试从URI参数中提取客户端信息
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
                        System.out.println("从URL参数提取的客户端信息: " + clientInfo);

                        // 解析客户端信息（格式：ip|uniqueId|name）
                        String[] parts = clientInfo.split("\\|");
                        if (parts.length >= 3) {
                            String ip = parts[0];
                            String uniqueId = parts[1];
                            String name = parts[2];

                            System.out.println("✓ URL解析成功:");
                            System.out.println("  IP: " + ip);
                            System.out.println("  UniqueID: " + uniqueId);
                            System.out.println("  Name: " + name);

                            // 将详细信息存储到session属性中
                            session.getAttributes().put("clientIp", ip);
                            session.getAttributes().put("clientUniqueId", uniqueId);
                            session.getAttributes().put("clientName", name);

                            System.out.println("最终客户端信息: " + clientInfo);
                            System.out.println("========================");
                            return clientInfo;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("URL参数解析失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // 降级处理：使用默认值
            String fallbackIp = "127.0.0.1";
            String fallbackUniqueId = "unknown_" + session.getId().substring(0, 8);
            String fallbackName = "未知用户";
            String fallbackInfo = fallbackIp + "|" + fallbackUniqueId + "|" + fallbackName;

            System.out.println("使用降级方案:");
            System.out.println("  IP: " + fallbackIp);
            System.out.println("  UniqueID: " + fallbackUniqueId);
            System.out.println("  Name: " + fallbackName);

            // 设置默认值到session属性
            session.getAttributes().put("clientIp", fallbackIp);
            session.getAttributes().put("clientUniqueId", fallbackUniqueId);
            session.getAttributes().put("clientName", fallbackName);

            System.out.println("最终客户端信息: " + fallbackInfo);
            System.out.println("========================");
            return fallbackInfo;

        } catch (Exception e) {
            System.err.println("提取客户端信息发生严重错误: " + e.getMessage());
            e.printStackTrace();

            // 紧急降级处理
            String emergencyIp = "127.0.0.1";
            String emergencyUniqueId = "emergency_" + session.getId().substring(0, 8);
            String emergencyName = "紧急用户";
            String emergencyInfo = emergencyIp + "|" + emergencyUniqueId + "|" + emergencyName;

            session.getAttributes().put("clientIp", emergencyIp);
            session.getAttributes().put("clientUniqueId", emergencyUniqueId);
            session.getAttributes().put("clientName", emergencyName);

            System.out.println("紧急降级处理: " + emergencyInfo);
            System.out.println("========================");
            return emergencyInfo;
        }
    }

    private String getClientIp(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();

        // 优先使用extractClientInfo中设置的IP
        String clientIp = (String) attributes.get("clientIp");
        if (clientIp != null && !clientIp.isEmpty() && !"unknown".equals(clientIp) && !"127.0.0.1".equals(clientIp)) {
            System.out.println("✓ 使用extractClientInfo设置的IP: " + clientIp);
            return clientIp;
        }

        // 备用方案：如果设置了127.0.0.1，也使用（表示本地连接）
        if (clientIp != null && !clientIp.isEmpty()) {
            System.out.println("✓ 使用备用IP: " + clientIp);
            return clientIp;
        }

        System.err.println("⚠ 无法获取客户端IP，使用默认值");
        return "127.0.0.1";
    }

    private String getClientUniqueId(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        String uniqueId = (String) attributes.get("clientUniqueId");

        if (uniqueId != null && !uniqueId.isEmpty() && !uniqueId.startsWith("unknown_") && !uniqueId.startsWith("emergency_")) {
            System.out.println("✓ 使用session中的UniqueID: " + uniqueId);
            return uniqueId;
        }

        // 如果有unknown前缀的ID，也使用（表示降级情况）
        if (uniqueId != null && !uniqueId.isEmpty()) {
            System.out.println("✓ 使用降级UniqueID: " + uniqueId);
            return uniqueId;
        }

        System.err.println("⚠ 无法获取UniqueID，生成默认值");
        return "unknown_" + session.getId().substring(0, 8);
    }

    private String getClientName(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        String name = (String) attributes.get("clientName");

        if (name != null && !name.isEmpty() && !"未知用户".equals(name) && !"紧急用户".equals(name)) {
            System.out.println("✓ 使用session中的用户名: " + name);
            return name;
        }

        // 如果是默认用户名，也使用
        if (name != null && !name.isEmpty()) {
            System.out.println("✓ 使用默认用户名: " + name);
            return name;
        }

        System.err.println("⚠ 无法获取用户名，使用默认值");
        return "未知用户";
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