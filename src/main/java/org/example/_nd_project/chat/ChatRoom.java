package org.example._nd_project.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_rooms")
public class ChatRoom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "task_id", nullable = false, unique = true)
    private Long taskId;
    @Column(name = "requester_member_id", nullable = false)
    private Long requesterMemberId;
    @Column(name = "worker_member_id", nullable = false)
    private Long workerMemberId;
    @Column(name = "task_title", nullable = false, length = 120)
    private String taskTitle;
    @Column(name = "last_message_preview", length = 500)
    private String lastMessagePreview;
    @Column(name = "last_message_at")
    private Instant lastMessageAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected ChatRoom() {}

    private ChatRoom(Long taskId, Long requesterMemberId, Long workerMemberId, String taskTitle) {
        this.taskId = taskId;
        this.requesterMemberId = requesterMemberId;
        this.workerMemberId = workerMemberId;
        this.taskTitle = taskTitle;
    }

    public static ChatRoom create(Long taskId, Long requesterMemberId, Long workerMemberId, String taskTitle) {
        if (requesterMemberId.equals(workerMemberId)) throw new IllegalArgumentException("의뢰인과 작업자는 서로 달라야 합니다.");
        return new ChatRoom(taskId, requesterMemberId, workerMemberId, taskTitle);
    }

    public boolean hasMember(Long memberId) { return requesterMemberId.equals(memberId) || workerMemberId.equals(memberId); }
    public Long otherMemberId(Long memberId) {
        if (requesterMemberId.equals(memberId)) return workerMemberId;
        if (workerMemberId.equals(memberId)) return requesterMemberId;
        throw new IllegalArgumentException("채팅방 참여자가 아닙니다.");
    }
    public void refreshLastMessage(String preview, Instant sentAt) { this.lastMessagePreview = preview; this.lastMessageAt = sentAt; }
    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getRequesterMemberId() { return requesterMemberId; }
    public Long getWorkerMemberId() { return workerMemberId; }
    public String getTaskTitle() { return taskTitle; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
