package org.example._nd_project.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true)
    private Long taskId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private Long revieweeId;

    @Column(nullable = false)
    private short rating;

    @Column(length = 1000)
    private String content;

    @Column(name = "deadline_met")
    private Boolean deadlineMet;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
    }

    public static Review create(Long taskId, Long reviewerId, Long revieweeId,
                                int rating, String content, boolean deadlineMet) {
        Review review = new Review();
        review.taskId = taskId;
        review.reviewerId = reviewerId;
        review.revieweeId = revieweeId;
        review.rating = (short) rating;
        review.content = content == null || content.isBlank() ? null : content.trim();
        review.deadlineMet = deadlineMet;
        return review;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getReviewerId() { return reviewerId; }
    public Long getRevieweeId() { return revieweeId; }
    public short getRating() { return rating; }
    public String getContent() { return content; }
    public Boolean getDeadlineMet() { return deadlineMet; }
    public Instant getCreatedAt() { return createdAt; }
}
