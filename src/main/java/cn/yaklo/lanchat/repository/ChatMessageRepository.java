package cn.yaklo.lanchat.repository;

import cn.yaklo.lanchat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.recalled = false ORDER BY cm.timestamp DESC")
    List<ChatMessage> findRecentMessages(Pageable pageable);

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.recalled = false AND cm.timestamp < :timestamp ORDER BY cm.timestamp DESC")
    List<ChatMessage> findMessagesBefore(@Param("timestamp") LocalDateTime timestamp, Pageable pageable);

    List<ChatMessage> findByUserIpOrderByTimestampDesc(String userIp);

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.recalled = false AND cm.id = :messageId AND cm.userIp = :userIp")
    ChatMessage findActiveMessageByIdAndUserIp(@Param("messageId") Long messageId, @Param("userIp") String userIp);

    long countByRecalledFalse();

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.recalled = false ORDER BY cm.timestamp ASC")
    List<ChatMessage> findAllActiveMessagesOrderByTimestamp();
}