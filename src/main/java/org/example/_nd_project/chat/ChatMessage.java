package org.example._nd_project.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "room_id", nullable = false)
    private Long roomId;
    @Column(name = "sender_id", nullable = false)
    private Long senderId;
    @Column(nullable = false, length = 2000)
    private String content;
    @Column(name = "attachment_name", length = 255)
    private String attachmentName;
    @Column(name = "attachment_object_path", length = 1500)
    private String attachmentObjectPath;
    @Column(name = "attachment_content_type", length = 150)
    private String attachmentContentType;
    @Column(name = "attachment_size")
    private Long attachmentSize;
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;
    @Column(name = "read_at")
    private Instant readAt;
    @Column(name = "moderated_by_admin_id")
    private Long moderatedByAdminId;
    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;
    @Column(name = "moderated_at")
    private Instant moderatedAt;

    protected ChatMessage() {}

    private ChatMessage(Long roomId, Long senderId, String content, String attachmentName,
                        String attachmentObjectPath, String attachmentContentType, Long attachmentSize, Instant sentAt) {
        this.roomId = roomId; this.senderId = senderId; this.content = content; this.attachmentName = attachmentName;
        this.attachmentObjectPath = attachmentObjectPath; this.attachmentContentType = attachmentContentType;
        this.attachmentSize = attachmentSize; this.sentAt = sentAt;
    }
    public static ChatMessage create(Long roomId, Long senderId, String content, String attachmentName,
                                     String attachmentObjectPath, String attachmentContentType, Long attachmentSize) {
        return new ChatMessage(roomId, senderId, content, attachmentName, attachmentObjectPath,
                attachmentContentType, attachmentSize, Instant.now());
    }
    public void blind(Long adminId, String reason, Instant moderatedAt) {
        if (adminId == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("블라인드 처리 사유를 입력해주세요.");
        }
        this.moderatedByAdminId = adminId;
        this.moderationReason = reason.trim();
        this.moderatedAt = moderatedAt;
    }
    public void restore() {
        this.moderatedByAdminId = null;
        this.moderationReason = null;
        this.moderatedAt = null;
    }
    public boolean isModerated() { return moderatedAt != null; }
    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public Long getSenderId() { return senderId; }
    public String getContent() { return content; }
    public String getAttachmentName() { return attachmentName; }
    public String getAttachmentObjectPath() { return attachmentObjectPath; }
    public String getAttachmentContentType() { return attachmentContentType; }
    public Long getAttachmentSize() { return attachmentSize; }
    public Instant getSentAt() { return sentAt; }
    public Instant getReadAt() { return readAt; }
    public Long getModeratedByAdminId() { return moderatedByAdminId; }
    public String getModerationReason() { return moderationReason; }
    public Instant getModeratedAt() { return moderatedAt; }
}
