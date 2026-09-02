package org.example._nd_project.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MemberNotificationRepository extends JpaRepository<MemberNotification, Long> {
    List<MemberNotification> findTop20ByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);
    long countByMemberIdAndReadAtIsNull(Long memberId);
    boolean existsByEventKey(String eventKey);
    Optional<MemberNotification> findByIdAndMemberId(Long id, Long memberId);

    @Modifying
    @Query("""
            update MemberNotification notification
               set notification.readAt = :readAt
             where notification.memberId = :memberId
               and notification.readAt is null
            """)
    int markAllRead(@Param("memberId") Long memberId, @Param("readAt") Instant readAt);
}
