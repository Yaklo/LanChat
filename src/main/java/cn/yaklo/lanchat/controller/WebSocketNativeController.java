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
        System.out.println("=== 接收到消息详细分析 ===");
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
        System.out.println("消息内容: " + content);
        System.out.println("========================");

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
            // 尝试从WebSocket连接的URI中提取客户端信息
            String uri = session.getUri().toString();
            System.out.println("WebSocket连接URI: " + uri);

            // 从URI参数中提取客户端信息
            if (uri.contains("client=")) {
                String[] params = uri.split("&");
                for (String param : params) {
                    if (param.startsWith("client=")) {
                        String clientInfo = param.substring(7); // 去掉"client="前缀
                        // URL解码
                        clientInfo = java.net.URLDecoder.decode(clientInfo, "UTF-8");
                        System.out.println("从URL参数中提取的客户端信息: " + clientInfo);

                        // 解析客户端信息（格式：ip|uniqueId|name）
                        String[] parts = clientInfo.split("\\|");
                        if (parts.length >= 3) {
                            String ip = parts[0];
                            String uniqueId = parts[1];
                            String name = parts[2];

                            System.out.println("解析的客户端信息:");
                            System.out.println("  IP: " + ip);
                            System.out.println("  UniqueID: " + uniqueId);
                            System.out.println("  Name: " + name);

                            // 将详细信息存储到session属性中
                            session.getAttributes().put("clientIp", ip);
                            session.getAttributes().put("clientUniqueId", uniqueId);
                            session.getAttributes().put("clientName", name);

                            return clientInfo;
                        }
                        return clientInfo;
                    }
                }
            }

            // 如果URI中没有客户端信息，使用session ID + 时间戳作为唯一标识
            String fallbackInfo = "Client_" + session.getId().substring(0, 8) + "_" + System.currentTimeMillis();
            System.out.println("URI中未找到客户端信息，使用默认标识: " + fallbackInfo);
            return fallbackInfo;
        } catch (Exception e) {
            System.err.println("提取客户端信息失败: " + e.getMessage());
            e.printStackTrace();
            // 降级处理：使用session ID作为客户端标识
            String fallbackInfo = "Client_" + session.getId().substring(0, 8);
            System.out.println("降级处理，使用session ID: " + fallbackInfo);
            return fallbackInfo;
        }
    }

    private String getClientIp(WebSocketSession session) {
        // 优先使用从URL参数中获取的IP
        Map<String, Object> attributes = session.getAttributes();
        String clientIp = (String) attributes.get("clientIp");

        if (clientIp != null && !clientIp.isEmpty()) {
            System.out.println("使用URL参数中的IP: " + clientIp);
            return clientIp;
        }

        // 备用方案：使用握手拦截器设置的IP
        clientIp = (String) attributes.get("clientIp_handshake");
        if (clientIp != null && !clientIp.isEmpty()) {
            System.out.println("使用握手拦截器设置的IP: " + clientIp);
            return clientIp;
        }

        // 最后的降级方案：使用默认的clientInfo
        String clientInfo = (String) attributes.get("clientInfo");
        if (clientInfo != null && !clientInfo.isEmpty()) {
            System.out.println("使用默认clientInfo: " + clientInfo);
            return clientInfo;
        }

        System.err.println("无法获取客户端IP，使用默认值");
        return "unknown";
    }

  private String getClientUniqueId(WebSocketSession session) {
      Map<String, Object> attributes = session.getAttributes();
      String uniqueId = (String) attributes.get("clientUniqueId");

      if (uniqueId != null && !uniqueId.isEmpty()) {
          return uniqueId;
      }

      return "unknown_" + session.getId().substring(0, 8);
  }

  private String getClientName(WebSocketSession session) {
      Map<String, Object> attributes = session.getAttributes();
      String name = (String) attributes.get("clientName");

      if (name != null && !name.isEmpty()) {
          return name;
      }

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