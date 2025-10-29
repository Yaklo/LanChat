package cn.yaklo.lanchat.dto;

import cn.yaklo.lanchat.entity.ChatFile;
import cn.yaklo.lanchat.entity.ChatMessage;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private Long id;
    private String userIp;
    private String uniqueId;
    private String userName;
    private String content;
    private String timestamp;
    private String messageType;
    private Boolean recalled;
    private String recalledAt;
    private Long fileId;
    private String fileName;
    private Long fileSize;

    // 新增字段：增强身份识别
    private String browserFingerprint;  // 浏览器指纹
    private String sessionId;          // Session ID片段
    private String displayName;        // 显示名称（可能包含IP信息）
    private Boolean isSystemMessage;   // 是否为系统消息
    private String messageSource;      // 消息来源标识（WebSocket/HTTP）

    public static ChatMessageDto fromEntity(ChatMessage message,
                                          ChatFile file) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setId(message.getId());
        dto.setUserIp(message.getUserIp());
        dto.setUniqueId(message.getUniqueId());
        dto.setUserName(message.getUserName());
        dto.setContent(message.getContent());
        dto.setTimestamp(message.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        dto.setMessageType(message.getMessageType().name());
        dto.setRecalled(message.getRecalled());

        if (message.getRecalledAt() != null) {
            dto.setRecalledAt(message.getRecalledAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        dto.setFileId(message.getFileId());

        if (file != null) {
            dto.setFileName(file.getOriginalName());
            dto.setFileSize(file.getFileSize());
        }

        // 填充新的增强字段
        enhanceMessageIdentity(dto, message);

        return dto;
    }

    /**
     * 增强消息身份信息
     */
    private static void enhanceMessageIdentity(ChatMessageDto dto, cn.yaklo.lanchat.entity.ChatMessage message) {
        // 从uniqueId中提取浏览器指纹和Session ID
        String uniqueId = message.getUniqueId();
        if (uniqueId != null) {
            // 提取浏览器指纹
            if (uniqueId.contains("#")) {
                String[] parts = uniqueId.split("#");
                if (parts.length >= 2) {
                    String fingerprintWithSession = parts[1];
                    int atIndex = fingerprintWithSession.indexOf('@');
                    if (atIndex > 0) {
                        dto.setBrowserFingerprint(fingerprintWithSession.substring(0, atIndex));
                    }
                }
            }

            // 提取Session ID
            int atIndex = uniqueId.lastIndexOf('@');
            if (atIndex > 0 && atIndex < uniqueId.length() - 1) {
                dto.setSessionId(uniqueId.substring(atIndex + 1));
            }
        }

        // 设置显示名称（包含IP信息，便于调试）
        String displayName = message.getUserName();
        if (message.getUserIp() != null && !message.getUserIp().equals("127.0.0.1")) {
            displayName += " (" + message.getUserIp() + ")";
        }
        dto.setDisplayName(displayName);

        // 设置系统消息标识
        dto.setIsSystemMessage(cn.yaklo.lanchat.entity.ChatMessage.MessageType.SYSTEM.equals(message.getMessageType()));

        // 设置消息来源（默认为WebSocket）
        dto.setMessageSource("WebSocket");
    }
}
