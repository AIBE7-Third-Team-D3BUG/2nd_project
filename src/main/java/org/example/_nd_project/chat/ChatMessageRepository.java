package org.example._nd_project.chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    interface WorkerResponseMetric {
        Long getMemberId();
        Double getAverageResponseSeconds();
        long getSampleCount();
    }

    interface UnreadCountMetric {
        Long getRoomId();
        long getCount();
    }

    List<ChatMessage> findTop100ByRoomIdOrderBySentAtDescIdDesc(Long roomId);
    List<ChatMessage> findByRoomId(Long roomId);
    Optional<ChatMessage> findTopByRoomIdOrderBySentAtDescIdDesc(Long roomId);
    long countByRoomId(Long roomId);
    long countByRoomIdAndSenderIdNotAndReadAtIsNull(Long roomId, Long senderId);
    @Query("""
            select message.roomId as roomId, count(message) as count
            from ChatMessage message
            where message.roomId in :roomIds
              and message.senderId <> :memberId
              and message.readAt is null
            group by message.roomId
            """)
    List<UnreadCountMetric> countUnreadByRoomIds(@Param("roomIds") List<Long> roomIds,
                                                  @Param("memberId") Long memberId);
    @Modifying
    @Query("update ChatMessage m set m.readAt = :readAt where m.roomId = :roomId and m.senderId <> :memberId and m.readAt is null")
    int markRead(@Param("roomId") Long roomId, @Param("memberId") Long memberId, @Param("readAt") Instant readAt);

    @Query(value = """
            with ordered_messages as (
                select room.worker_member_id as member_id,
                       message.sender_id,
                       message.sent_at,
                       lag(message.sender_id) over (
                           partition by message.room_id order by message.sent_at, message.id
                       ) as previous_sender_id,
                       lag(message.sent_at) over (
                           partition by message.room_id order by message.sent_at, message.id
                       ) as previous_sent_at
                from chat_messages message
                join chat_rooms room on room.id = message.room_id
                where room.worker_member_id in (:memberIds)
                  and message.moderated_at is null
            ), worker_responses as (
                select member_id,
                       extract(epoch from (sent_at - previous_sent_at))::double precision as response_seconds
                from ordered_messages
                where sender_id = member_id
                  and previous_sender_id <> member_id
                  and previous_sent_at is not null
                  and sent_at >= previous_sent_at
                  and sent_at - previous_sent_at <= interval '24 hours'
            )
            select member_id as memberId,
                   avg(response_seconds) as averageResponseSeconds,
                   count(*) as sampleCount
            from worker_responses
            group by member_id
            """, nativeQuery = true)
    List<WorkerResponseMetric> findWorkerResponseMetrics(@Param("memberIds") List<Long> memberIds);
}
