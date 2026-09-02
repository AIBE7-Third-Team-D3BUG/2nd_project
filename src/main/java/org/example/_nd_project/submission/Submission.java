package org.example._nd_project.submission;

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
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true)
    private Long taskId;

    @Column(name = "worker_id", nullable = false)
    private Long workerId;

    @Column(name = "result_description", nullable = false, columnDefinition = "text")
    private String resultDescription;

    @Column(name = "result_file_url", length = 1500)
    private String resultFileUrl;

    @Column(name = "actual_minutes", nullable = false)
    private int actualMinutes;

    @Column(name = "requester_note", length = 1000)
    private String requesterNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "deadline_status", nullable = false, length = 20)
    private SubmissionDeadlineAssessment.Status deadlineStatus;

    @Column(name = "late_minutes", nullable = false)
    private int lateMinutes;

    @Column(name = "severe_threshold_minutes", nullable = false)
    private int severeThresholdMinutes;

    @Column(name = "deadline_assessed_at", nullable = false)
    private Instant deadlineAssessedAt;

    @Column(name = "penalty_exempted", nullable = false)
    private boolean penaltyExempted;

    @Column(name = "penalty_exemption_reason", length = 450)
    private String penaltyExemptionReason;

    @Column(name = "penalty_exempted_by")
    private Long penaltyExemptedBy;

    @Column(name = "penalty_exempted_at")
    private Instant penaltyExemptedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Submission() {
    }

    private Submission(Long taskId, Long workerId, String resultDescription,
                       String resultFileUrl, int actualMinutes,
                       SubmissionDeadlineAssessment deadlineAssessment,
                       Instant deadlineAssessedAt) {
        this.taskId = taskId;
        this.workerId = workerId;
        this.resultDescription = resultDescription;
        this.resultFileUrl = resultFileUrl;
        this.actualMinutes = actualMinutes;
        recordDeadlineAssessment(deadlineAssessment, deadlineAssessedAt);
    }

    public static Submission create(Long taskId, Long workerId, String resultDescription,
                                    String resultFileUrl, int actualMinutes,
                                    SubmissionDeadlineAssessment deadlineAssessment,
                                    Instant deadlineAssessedAt) {
        return new Submission(taskId, workerId, resultDescription, resultFileUrl, actualMinutes,
                deadlineAssessment, deadlineAssessedAt);
    }

    public void resubmit(String resultDescription, String resultFileUrl, int actualMinutes) {
        this.resultDescription = resultDescription;
        this.resultFileUrl = resultFileUrl;
        this.actualMinutes = actualMinutes;
    }

    public void recordRevisionRequest(String requesterNote) {
        this.requesterNote = requesterNote;
    }

    private void recordDeadlineAssessment(SubmissionDeadlineAssessment assessment, Instant assessedAt) {
        Objects.requireNonNull(assessment, "제출 지연 판정이 필요합니다.");
        if (!assessment.submitted() || assessment.status() == SubmissionDeadlineAssessment.Status.UPCOMING) {
            throw new IllegalArgumentException("제출 완료 상태만 이력으로 저장할 수 있습니다.");
        }
        this.deadlineStatus = assessment.status();
        this.lateMinutes = Math.toIntExact(assessment.lateMinutes());
        this.severeThresholdMinutes = Math.toIntExact(assessment.severeThresholdMinutes());
        this.deadlineAssessedAt = Objects.requireNonNull(assessedAt, "제출 판정 시각이 필요합니다.");
    }

    public SubmissionDeadlineAssessment getDeadlineAssessment() {
        return new SubmissionDeadlineAssessment(
                deadlineStatus,
                true,
                lateMinutes,
                severeThresholdMinutes
        );
    }

    public void exemptDelayPenalty(Long adminId, String reason, Instant exemptedAt) {
        if (deadlineStatus != SubmissionDeadlineAssessment.Status.LATE
                && deadlineStatus != SubmissionDeadlineAssessment.Status.SEVERE) {
            throw new IllegalStateException("지연 또는 심각 지연 제출만 패널티를 면제할 수 있습니다.");
        }
        if (penaltyExempted) {
            throw new IllegalStateException("이미 패널티가 면제된 제출입니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("패널티 면제 사유를 입력해주세요.");
        }
        this.penaltyExempted = true;
        this.penaltyExemptionReason = reason.trim();
        this.penaltyExemptedBy = Objects.requireNonNull(adminId, "면제 처리 관리자 정보가 필요합니다.");
        this.penaltyExemptedAt = Objects.requireNonNull(exemptedAt, "면제 처리 시각이 필요합니다.");
    }

    public void restoreDelayPenalty() {
        if (!penaltyExempted) {
            throw new IllegalStateException("면제되지 않은 제출입니다.");
        }
        this.penaltyExempted = false;
        this.penaltyExemptionReason = null;
        this.penaltyExemptedBy = null;
        this.penaltyExemptedAt = null;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getWorkerId() { return workerId; }
    public String getResultDescription() { return resultDescription; }
    public String getResultFileUrl() { return resultFileUrl; }
    public int getActualMinutes() { return actualMinutes; }
    public String getRequesterNote() { return requesterNote; }
    public boolean isPenaltyExempted() { return penaltyExempted; }
    public String getPenaltyExemptionReason() { return penaltyExemptionReason; }
    public Long getPenaltyExemptedBy() { return penaltyExemptedBy; }
    public Instant getPenaltyExemptedAt() { return penaltyExemptedAt; }
    public Instant getDeadlineAssessedAt() { return deadlineAssessedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
