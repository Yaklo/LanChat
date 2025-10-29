package cn.yaklo.lanchat.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 45)
    private String userIp;

    @Column(nullable = false, length = 100)
    private String userName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType messageType;

    @Column(name = "is_recalled")
    private Boolean recalled = false;

    @Column(name = "recalled_at")
    private LocalDateTime recalledAt;

    @Column(name = "file_id")
    private Long fileId;

    public enum MessageType {
        TEXT, FILE, SYSTEM
    }

    public ChatMessage(String userIp, String userName, String content, MessageType messageType) {
        this.userIp = userIp;
        this.userName = userName;
        this.content = content;
        this.messageType = messageType;
        this.timestamp = LocalDateTime.now();
        this.recalled = false;
    }

    public void recall() {
        this.recalled = true;
        this.recalledAt = LocalDateTime.now();
        this.content = "[消息已被撤回]";
    }
}