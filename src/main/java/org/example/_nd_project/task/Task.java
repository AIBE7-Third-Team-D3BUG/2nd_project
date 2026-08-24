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

    @Column(name = "reference_file_url", length = 1500)
    private String referenceFileUrl;

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
                 String deliverableDescription, String referenceFileUrl) {
        this.requesterId = requesterId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.requiredSkillTags = requiredSkillTags.clone();
        this.requestedMinutes = requestedMinutes;
        this.deadlineAt = deadlineAt;
        this.deliverableDescription = deliverableDescription;
        this.referenceFileUrl = referenceFileUrl;
    }

    public static Task create(Long requesterId, String title, String description, TaskCategory category,
                              String[] requiredSkillTags, int requestedMinutes, Instant deadlineAt,
                              String deliverableDescription, String referenceFileUrl) {
        return new Task(requesterId, title, description, category, requiredSkillTags, requestedMinutes,
                deadlineAt, deliverableDescription, referenceFileUrl);
    }

    public void attachReferenceFile(String objectPath) {
        this.referenceFileUrl = objectPath;
    }

    public void updateDetails(String title, String description, TaskCategory category,
                              String[] requiredSkillTags, int requestedMinutes, Instant deadlineAt,
                              String deliverableDescription, String referenceFileUrl) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.requiredSkillTags = requiredSkillTags.clone();
        this.requestedMinutes = requestedMinutes;
        this.deadlineAt = deadlineAt;
        this.deliverableDescription = deliverableDescription;
        this.referenceFileUrl = referenceFileUrl;
    }

    public void cancel(Instant cancelledAt) {
        this.status = TaskStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    public void assignWorker(Long workerId, Instant matchedAt) {
        this.workerId = workerId;
        this.status = TaskStatus.MATCHED;
        this.matchedAt = matchedAt;
    }

    public void unassignWorker() {
        this.workerId = null;
        this.matchedAt = null;
        this.status = TaskStatus.OPEN;
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
    public String getReferenceFileUrl() { return referenceFileUrl; }
    public TaskStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
