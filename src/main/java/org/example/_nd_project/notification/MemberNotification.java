package org.example._nd_project.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "member_notifications")
public class MemberNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MemberNotificationType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Column(name = "event_key", nullable = false, unique = true, length = 180)
    private String eventKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected MemberNotification() {
    }

    public static MemberNotification create(Long memberId, MemberNotificationType type,
                                            String title, String message, String targetUrl,
                                            String eventKey) {
        MemberNotification notification = new MemberNotification();
        notification.memberId = Objects.requireNonNull(memberId, "알림 수신 회원이 필요합니다.");
        notification.type = Objects.requireNonNull(type, "알림 유형이 필요합니다.");
        notification.title = requireText(title, "알림 제목이 필요합니다.", 120);
        notification.message = requireText(message, "알림 내용이 필요합니다.", 500);
        notification.targetUrl = normalizeTargetUrl(targetUrl);
        notification.eventKey = requireText(eventKey, "알림 이벤트 키가 필요합니다.", 180);
        return notification;
    }

    public void markRead(Instant readAt) {
        if (this.readAt == null) {
            this.readAt = Objects.requireNonNull(readAt, "알림 확인 시각이 필요합니다.");
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    private static String requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("입력값은 " + maxLength + "자 이하이어야 합니다.");
        }
        return normalized;
    }

    private static String normalizeTargetUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!normalized.startsWith("/") || normalized.startsWith("//") || normalized.length() > 500) {
            throw new IllegalArgumentException("알림 이동 경로가 올바르지 않습니다.");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public MemberNotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getTargetUrl() { return targetUrl; }
    public String getEventKey() { return eventKey; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
