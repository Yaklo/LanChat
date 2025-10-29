package cn.yaklo.lanchat.controller;

import cn.yaklo.lanchat.entity.ChatFile;
import cn.yaklo.lanchat.service.FileService;
import cn.yaklo.lanchat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@Controller
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private UserService userService;

    @PostMapping("/api/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();

        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "文件不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            String userIp = userService.getClientIp(request);
            ChatFile savedFile = fileService.uploadFile(file, userIp);

            response.put("success", true);
            response.put("fileId", savedFile.getId());
            response.put("fileName", savedFile.getOriginalName());
            response.put("fileSize", savedFile.getFileSize());
            response.put("message", "文件上传成功");

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "文件上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/api/file/{fileId}")
    @ResponseBody
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) {
        ChatFile chatFile = fileService.getFileById(fileId);
        if (chatFile == null) {
            return ResponseEntity.notFound().build();
        }

        java.io.File file = fileService.getPhysicalFile(fileId);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        String contentType = chatFile.getContentType();
        if (contentType == null) {
            try {
                contentType = Files.probeContentType(file.toPath());
            } catch (IOException e) {
                contentType = "application/octet-stream";
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + chatFile.getOriginalName() + "\"")
                .body(resource);
    }

    @GetMapping("/api/file/{fileId}/info")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFileInfo(@PathVariable Long fileId) {
        ChatFile chatFile = fileService.getFileById(fileId);
        if (chatFile == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("id", chatFile.getId());
        fileInfo.put("originalName", chatFile.getOriginalName());
        fileInfo.put("fileSize", chatFile.getFileSize());
        fileInfo.put("contentType", chatFile.getContentType());
        fileInfo.put("uploadedBy", chatFile.getUploadedBy());
        fileInfo.put("uploadedAt", chatFile.getUploadedAt());

        return ResponseEntity.ok(fileInfo);
    }
}