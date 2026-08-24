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
}
