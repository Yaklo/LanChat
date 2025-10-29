package cn.yaklo.lanchat.repository;

import cn.yaklo.lanchat.entity.ChatFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatFileRepository extends JpaRepository<ChatFile, Long> {

    Optional<ChatFile> findByStoredName(String storedName);

    List<ChatFile> findByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    boolean existsByStoredName(String storedName);
}