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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @Column(name = "attachment_path", length = 1500)
    private String attachmentPath;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    @Column(name = "sent_at", nullable = false, insertable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected ChatMessage() {
    }

    public ChatMessage(Long roomId, Long senderId, String content) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
    }

    public ChatMessage(Long roomId, Long senderId, String content, String attachmentName, String attachmentPath, Long attachmentSize) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.attachmentName = attachmentName;
        this.attachmentPath = attachmentPath;
        this.attachmentSize = attachmentSize;
    }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public Long getSenderId() { return senderId; }
    public String getContent() { return content; }
    public String getAttachmentName() { return attachmentName; }
    public String getAttachmentPath() { return attachmentPath; }
    public Long getAttachmentSize() { return attachmentSize; }
    public Instant getSentAt() { return sentAt; }
}
