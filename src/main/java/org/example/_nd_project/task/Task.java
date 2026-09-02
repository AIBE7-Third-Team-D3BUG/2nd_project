package org.example._nd_project.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "worker_id")
    private Long workerId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskCategory category;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "required_skill_tags", nullable = false, columnDefinition = "varchar(50)[]")
    private String[] requiredSkillTags = new String[0];

    @Column(name = "requested_minutes", nullable = false)
    private int requestedMinutes;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "deliverable_description", nullable = false, length = 500)
    private String deliverableDescription;

    @Column(name = "revision_limit", nullable = false)
    private int revisionLimit;

    @Column(name = "reference_link_url", length = 1500)
    private String referenceLinkUrl;

    @Column(name = "attachment_object_path", length = 1500)
    private String attachmentObjectPath;

    @Column(length = 1000)
    private String caution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.OPEN;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Task() {
    }

    private Task(Long requesterId, String title, String description, TaskCategory category,
                 String[] requiredSkillTags, int requestedMinutes, Instant deadlineAt,
                 String deliverableDescription, String referenceLinkUrl, String attachmentObjectPath) {
        this.requesterId = requesterId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.requiredSkillTags = requiredSkillTags.clone();
        this.requestedMinutes = requestedMinutes;
        this.deadlineAt = deadlineAt;
        this.deliverableDescription = deliverableDescription;
        this.referenceLinkUrl = referenceLinkUrl;
        this.attachmentObjectPath = attachmentObjectPath;
    }

    public static Task create(Long requesterId, String title, String description, TaskCategory category,
                              String[] requiredSkillTags, int requestedMinutes, Instant deadlineAt,
                              String deliverableDescription, String referenceFileUrl) {
        return create(requesterId, title, description, category, requiredSkillTags, requestedMinutes,
                deadlineAt, deliverableDescription,
                isExternalUrl(referenceFileUrl) ? referenceFileUrl : null,
                isExternalUrl(referenceFileUrl) ? null : referenceFileUrl);
    }

    public static Task create(Long requesterId, String title, String description, TaskCategory category,
                              String[] requiredSkillTags, int requestedMinutes, Instant deadlineAt,
                              String deliverableDescription, String referenceLinkUrl, String attachmentObjectPath) {
        return new Task(requesterId, title, description, category, requiredSkillTags, requestedMinutes,
                deadlineAt, deliverableDescription, referenceLinkUrl, attachmentObjectPath);
    }

    public void attachReferenceFile(String objectPath) {
        this.attachmentObjectPath = objectPath;
    }

    public void updateDetails(String title, String description, TaskCategory category,
                              String[] requiredSkillTags, int requestedMinutes, Instant deadlineAt,
                              String deliverableDescription, String referenceLinkUrl, String attachmentObjectPath) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.requiredSkillTags = requiredSkillTags.clone();
        this.requestedMinutes = requestedMinutes;
        this.deadlineAt = deadlineAt;
        this.deliverableDescription = deliverableDescription;
        this.referenceLinkUrl = referenceLinkUrl;
        this.attachmentObjectPath = attachmentObjectPath;
    }

    public void cancel(Instant cancelledAt) {
        this.status = TaskStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    public void assignWorker(Long workerId, Instant matchedAt) {
        requireStatus(TaskStatus.OPEN, "모집 중인 업무만 작업자를 선택할 수 있습니다.");
        this.workerId = workerId;
        this.status = TaskStatus.MATCHED;
        this.matchedAt = matchedAt;
    }

    public void unassignWorker() {
        requireStatus(TaskStatus.MATCHED, "업무 시작 전까지만 작업자 선택을 취소할 수 있습니다.");
        this.workerId = null;
        this.matchedAt = null;
        this.status = TaskStatus.OPEN;
    }

    public void startWork(Long memberId, Instant startedAt) {
        requireWorker(memberId);
        requireStatus(TaskStatus.MATCHED, "매칭이 완료된 업무만 시작할 수 있습니다.");
        this.status = TaskStatus.IN_PROGRESS;
        this.startedAt = startedAt;
    }

    public void submitResult(Long memberId, Instant submittedAt) {
        requireWorker(memberId);
        requireStatus(TaskStatus.IN_PROGRESS, "진행 중인 업무만 결과를 제출할 수 있습니다.");
        this.status = TaskStatus.SUBMITTED;
        this.submittedAt = submittedAt;
    }

    public void requestRevision(Long memberId) {
        requireRequester(memberId);
        requireStatus(TaskStatus.SUBMITTED, "제출된 결과만 수정을 요청할 수 있습니다.");
        this.status = TaskStatus.IN_PROGRESS;
    }

    public void complete(Long memberId, Instant completedAt) {
        requireRequester(memberId);
        requireStatus(TaskStatus.SUBMITTED, "제출된 결과만 완료 승인할 수 있습니다.");
        this.status = TaskStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public void openDispute(Long memberId) {
        if (!isParticipant(memberId)) {
            throw new IllegalArgumentException("업무 참여자만 문제를 신고할 수 있습니다.");
        }
        requireStatus(TaskStatus.SUBMITTED, "제출된 결과에 대해서만 문제를 신고할 수 있습니다.");
        this.status = TaskStatus.DISPUTED;
    }

    public void resolveDispute(boolean accepted, Instant resolvedAt) {
        requireStatus(TaskStatus.DISPUTED, "분쟁 중인 업무만 관리자 처리할 수 있습니다.");
        if (accepted) {
            this.status = TaskStatus.CANCELLED;
            this.cancelledAt = resolvedAt;
            return;
        }
        this.status = TaskStatus.SUBMITTED;
    }

    public void resolveWorkerDispute(Instant resolvedAt) {
        requireStatus(TaskStatus.DISPUTED, "분쟁 중인 업무만 관리자 처리할 수 있습니다.");
        this.status = TaskStatus.COMPLETED;
        this.completedAt = resolvedAt;
    }

    public boolean isRequester(Long memberId) {
        return Objects.equals(requesterId, memberId);
    }

    public boolean isWorker(Long memberId) {
        return workerId != null && Objects.equals(workerId, memberId);
    }

    public boolean isParticipant(Long memberId) {
        return isRequester(memberId) || isWorker(memberId);
    }

    private void requireRequester(Long memberId) {
        if (!isRequester(memberId)) {
            throw new IllegalArgumentException("의뢰인만 처리할 수 있습니다.");
        }
    }

    private void requireWorker(Long memberId) {
        if (!isWorker(memberId)) {
            throw new IllegalArgumentException("선택된 작업자만 처리할 수 있습니다.");
        }
    }

    private void requireStatus(TaskStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    public Long getId() { return id; }
    public Long getRequesterId() { return requesterId; }
    public Long getWorkerId() { return workerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskCategory getCategory() { return category; }
    public String[] getRequiredSkillTags() { return requiredSkillTags.clone(); }
    public int getRequestedMinutes() { return requestedMinutes; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public String getDeliverableDescription() { return deliverableDescription; }
    public String getReferenceLinkUrl() { return referenceLinkUrl; }
    public String getAttachmentObjectPath() { return attachmentObjectPath; }
    public TaskStatus getStatus() { return status; }
    public Instant getMatchedAt() { return matchedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static boolean isExternalUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }
}
