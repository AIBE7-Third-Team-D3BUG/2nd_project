package org.example._nd_project.chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop100ByRoomIdOrderBySentAtDescIdDesc(Long roomId);
    List<ChatMessage> findByRoomId(Long roomId);
    Optional<ChatMessage> findTopByRoomIdOrderBySentAtDescIdDesc(Long roomId);
    long countByRoomId(Long roomId);
    long countByRoomIdAndSenderIdNotAndReadAtIsNull(Long roomId, Long senderId);
    @Modifying
    @Query("update ChatMessage m set m.readAt = :readAt where m.roomId = :roomId and m.senderId <> :memberId and m.readAt is null")
    int markRead(@Param("roomId") Long roomId, @Param("memberId") Long memberId, @Param("readAt") Instant readAt);
}
