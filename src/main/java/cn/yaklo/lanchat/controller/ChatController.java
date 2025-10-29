package cn.yaklo.lanchat.controller;

import cn.yaklo.lanchat.dto.ChatMessageDto;
import cn.yaklo.lanchat.entity.ChatFile;
import cn.yaklo.lanchat.entity.ChatMessage;
import cn.yaklo.lanchat.service.ChatService;
import cn.yaklo.lanchat.service.FileService;
import cn.yaklo.lanchat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private FileService fileService;

    @Autowired
    private UserService userService;

    @GetMapping("/")
    public String chatPage(HttpServletRequest request, Model model) {
        String userIp = userService.getClientIp(request);
        String uniqueUserId = userService.getUniqueUserId(request);
        String userName = userService.getUserName(request);
        String displayName = userService.generateDisplayName(userIp, uniqueUserId);

        model.addAttribute("userIp", userIp);
        model.addAttribute("uniqueUserId", uniqueUserId);
        model.addAttribute("userName", userName);
        model.addAttribute("displayName", displayName);
        return "chat";
    }

    @GetMapping("/api/messages")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String before) {

        List<ChatMessage> messages;
        if (before != null && !before.isEmpty()) {
            LocalDateTime timestamp = LocalDateTime.parse(before + " 00:00:00",
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            messages = chatService.getMessagesBefore(timestamp, size);
        } else {
            messages = chatService.getRecentMessages(size);
        }

        List<ChatMessageDto> messageDtos = messages.stream()
                .map(message -> {
                    ChatFile file = null;
                    if (message.getFileId() != null) {
                        file = fileService.getFileById(message.getFileId());
                    }
                    return ChatMessageDto.fromEntity(message, file);
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("messages", messageDtos);
        response.put("totalCount", chatService.getTotalMessageCount());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/recall/{messageId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> recallMessage(
            @PathVariable Long messageId,
            HttpServletRequest request) {

        String userIp = userService.getClientIp(request);
        ChatMessage recalledMessage = chatService.recallMessage(messageId, userIp);

        Map<String, String> response = new HashMap<>();
        if (recalledMessage != null) {
            response.put("status", "success");
            response.put("message", "消息已撤回");
        } else {
            response.put("status", "error");
            response.put("message", "无法撤回该消息");
        }

        return ResponseEntity.ok(response);
    }
}