package org.example._nd_project.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

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

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Submission() {
    }

    private Submission(Long taskId, Long workerId, String resultDescription,
                       String resultFileUrl, int actualMinutes) {
        this.taskId = taskId;
        this.workerId = workerId;
        this.resultDescription = resultDescription;
        this.resultFileUrl = resultFileUrl;
        this.actualMinutes = actualMinutes;
    }

    public static Submission create(Long taskId, Long workerId, String resultDescription,
                                    String resultFileUrl, int actualMinutes) {
        return new Submission(taskId, workerId, resultDescription, resultFileUrl, actualMinutes);
    }

    public void resubmit(String resultDescription, String resultFileUrl, int actualMinutes) {
        this.resultDescription = resultDescription;
        this.resultFileUrl = resultFileUrl;
        this.actualMinutes = actualMinutes;
    }

    public void recordRevisionRequest(String requesterNote) {
        this.requesterNote = requesterNote;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getWorkerId() { return workerId; }
    public String getResultDescription() { return resultDescription; }
    public String getResultFileUrl() { return resultFileUrl; }
    public int getActualMinutes() { return actualMinutes; }
    public String getRequesterNote() { return requesterNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
