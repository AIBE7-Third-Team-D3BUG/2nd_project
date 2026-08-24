package org.example._nd_project.volunteer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "volunteer")
public class Volunteer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VolunteerStatus status = VolunteerStatus.APPLIED;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Volunteer() {
    }

    private Volunteer(Long taskId, Long memberId, String message) {
        this.taskId = taskId;
        this.memberId = memberId;
        this.message = message;
        this.status = VolunteerStatus.APPLIED;
    }

    public static Volunteer create(Long taskId, Long memberId, String message) {
        return new Volunteer(taskId, memberId, message);
    }

    public void accept() {
        this.status = VolunteerStatus.ACCEPTED;
    }

    public void reject() {
        this.status = VolunteerStatus.REJECTED;
    }

    public void cancel() {
        this.status = VolunteerStatus.CANCELLED;
    }

    public void resetToApplied() {
        this.status = VolunteerStatus.APPLIED;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getMemberId() { return memberId; }
    public String getMessage() { return message; }
    public VolunteerStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}