package cn.yaklo.lanchat.service;

import cn.yaklo.lanchat.entity.ChatFile;
import cn.yaklo.lanchat.repository.ChatFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {

    private final String UPLOAD_DIR = "./data/upfile";

    @Autowired
    private ChatFileRepository chatFileRepository;

    public ChatFile uploadFile(MultipartFile file, String userIp) throws IOException {
        // 确保上传目录存在
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedName = UUID.randomUUID().toString() + extension;

        // 保存文件
        Path filePath = uploadPath.resolve(storedName);
        Files.copy(file.getInputStream(), filePath);

        // 保存文件信息到数据库
        ChatFile chatFile = new ChatFile(
                originalFilename,
                storedName,
                filePath.toString(),
                file.getSize(),
                file.getContentType(),
                userIp
        );

        return chatFileRepository.save(chatFile);
    }

    public ChatFile getFileById(Long fileId) {
        return chatFileRepository.findById(fileId).orElse(null);
    }

    public ChatFile getFileByStoredName(String storedName) {
        return chatFileRepository.findByStoredName(storedName).orElse(null);
    }

    public List<ChatFile> getFilesByUser(String userIp) {
        return chatFileRepository.findByUploadedByOrderByUploadedAtDesc(userIp);
    }

    public boolean deleteFile(Long fileId, String userIp) {
        ChatFile chatFile = chatFileRepository.findById(fileId).orElse(null);
        if (chatFile != null && chatFile.getUploadedBy().equals(userIp)) {
            try {
                // 删除物理文件
                Path filePath = Paths.get(chatFile.getFilePath());
                Files.deleteIfExists(filePath);

                // 删除数据库记录
                chatFileRepository.delete(chatFile);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public File getPhysicalFile(Long fileId) {
        ChatFile chatFile = getFileById(fileId);
        if (chatFile != null) {
            return new File(chatFile.getFilePath());
        }
        return null;
    }
}