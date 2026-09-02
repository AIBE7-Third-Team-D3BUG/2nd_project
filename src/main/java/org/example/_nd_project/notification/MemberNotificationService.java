package org.example._nd_project.notification;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class MemberNotificationService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final MemberNotificationRepository notificationRepository;

    public MemberNotificationService(MemberNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void createIfAbsent(Long memberId, MemberNotificationType type,
                               String title, String message, String targetUrl,
                               String eventKey) {
        if (notificationRepository.existsByEventKey(eventKey)) return;
        notificationRepository.save(MemberNotification.create(
                memberId, type, title, message, targetUrl, eventKey));
    }

    @Transactional(readOnly = true)
    public MemberNotificationCenter getCenter(Long memberId) {
        var notifications = notificationRepository
                .findTop20ByMemberIdOrderByCreatedAtDescIdDesc(memberId)
                .stream()
                .map(this::toView)
                .toList();
        return new MemberNotificationCenter(
                notificationRepository.countByMemberIdAndReadAtIsNull(memberId),
                notifications
        );
    }

    @Transactional
    public String markReadAndGetTarget(Long memberId, Long notificationId) {
        MemberNotification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."));
        notification.markRead(Instant.now());
        return notification.getTargetUrl() == null ? "/profile#notifications" : notification.getTargetUrl();
    }

    @Transactional
    public void markAllRead(Long memberId) {
        notificationRepository.markAllRead(memberId, Instant.now());
    }

    private MemberNotificationView toView(MemberNotification notification) {
        return new MemberNotificationView(
                notification.getId(),
                notification.getType().name(),
                tone(notification.getType()),
                notification.getTitle(),
                notification.getMessage(),
                notification.getTargetUrl(),
                notification.isRead(),
                notification.getCreatedAt() == null
                        ? "방금 전"
                        : DATE_TIME.format(notification.getCreatedAt().atZone(KOREA))
        );
    }

    private String tone(MemberNotificationType type) {
        return switch (type) {
            case PENALTY_EXEMPTED -> "success";
            case APPLICATION_RESTRICTED, PENALTY_RESTORED -> "danger";
            case DELAY_RECORDED, DELAY_WARNING -> "warning";
        };
    }
}
