package org.example._nd_project.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "disputes")
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true)
    private Long taskId;

    @Column(name = "opened_by_member_id", nullable = false)
    private Long openedByMemberId;

    @Column(name = "dispute_type", nullable = false, length = 50)
    private String disputeType;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "evidence_url", length = 1500)
    private String evidenceUrl;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Dispute() {
    }

    public static Dispute open(Long taskId, Long memberId, String description) {
        Dispute dispute = new Dispute();
        dispute.taskId = taskId;
        dispute.openedByMemberId = memberId;
        dispute.disputeType = "RESULT_ISSUE";
        dispute.description = description;
        return dispute;
    }

    public void startReview() {
        if (!"OPEN".equals(status)) {
            throw new IllegalStateException("접수 상태의 분쟁만 검토를 시작할 수 있습니다.");
        }
        status = "UNDER_REVIEW";
    }

    public void resolve(boolean accepted, String resolutionNote, Instant resolvedAt) {
        if (!("OPEN".equals(status) || "UNDER_REVIEW".equals(status))) {
            throw new IllegalStateException("이미 처리가 끝난 분쟁입니다.");
        }
        if (resolutionNote == null || resolutionNote.isBlank()) {
            throw new IllegalArgumentException("분쟁 처리 메모를 입력해주세요.");
        }
        this.status = accepted ? "RESOLVED" : "REJECTED";
        this.resolutionNote = resolutionNote.trim();
        this.resolvedAt = resolvedAt;
    }

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getOpenedByMemberId() { return openedByMemberId; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getResolutionNote() { return resolutionNote; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
