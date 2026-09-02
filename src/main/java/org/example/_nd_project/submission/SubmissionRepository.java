package org.example._nd_project.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findByTaskId(Long taskId);

    @Query("""
            select submission.workerId as workerId,
                   count(submission) as submissionCount,
                   sum(case when submission.deadlineStatus in :metStatuses then 1 else 0 end) as deadlineMetCount,
                   sum(case when submission.deadlineStatus = :lateStatus then 1 else 0 end) as lateCount,
                   sum(case when submission.deadlineStatus = :severeStatus then 1 else 0 end) as severeCount,
                   sum(case when submission.deadlineStatus = :lateStatus then 1
                            when submission.deadlineStatus = :severeStatus then 2 else 0 end) as delayPoints
              from Submission submission
             where submission.workerId in :workerIds
               and submission.deadlineAssessedAt >= :since
             group by submission.workerId
            """)
    List<WorkerDelayMetric> queryWorkerDelayMetrics(
            @Param("workerIds") Collection<Long> workerIds,
            @Param("since") Instant since,
            @Param("metStatuses") Collection<SubmissionDeadlineAssessment.Status> metStatuses,
            @Param("lateStatus") SubmissionDeadlineAssessment.Status lateStatus,
            @Param("severeStatus") SubmissionDeadlineAssessment.Status severeStatus
    );

    default List<WorkerDelayMetric> findWorkerDelayMetrics(Collection<Long> workerIds, Instant since) {
        if (workerIds == null || workerIds.isEmpty()) {
            return List.of();
        }
        return queryWorkerDelayMetrics(
                workerIds,
                since,
                List.of(SubmissionDeadlineAssessment.Status.ON_TIME, SubmissionDeadlineAssessment.Status.GRACE),
                SubmissionDeadlineAssessment.Status.LATE,
                SubmissionDeadlineAssessment.Status.SEVERE
        );
    }

    interface WorkerDelayMetric {
        Long getWorkerId();
        Long getSubmissionCount();
        Long getDeadlineMetCount();
        Long getLateCount();
        Long getSevereCount();
        Long getDelayPoints();
    }
}
