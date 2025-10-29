package cn.yaklo.lanchat.dto;

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
    private String userName;
    private String content;
    private String timestamp;
    private String messageType;
    private Boolean recalled;
    private String recalledAt;
    private Long fileId;
    private String fileName;
    private Long fileSize;

    public static ChatMessageDto fromEntity(cn.yaklo.lanchat.entity.ChatMessage message,
                                          cn.yaklo.lanchat.entity.ChatFile file) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setId(message.getId());
        dto.setUserIp(message.getUserIp());
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

        return dto;
    }
}