package org.example._nd_project.submission;

import org.example._nd_project.task.Task;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class SubmissionDeadlinePolicy {

    static final long GRACE_MINUTES = 10;
    static final long MIN_SEVERE_DELAY_MINUTES = 30;
    static final long MAX_SEVERE_DELAY_MINUTES = 120;

    public SubmissionDeadlineAssessment assess(Task task, Submission submission, Instant now) {
        Instant deadline = task.getDeadlineAt();
        Instant submittedAt = firstSubmissionAt(task, submission);
        boolean submitted = submittedAt != null;
        Instant referenceTime = submitted ? submittedAt : now;
        long severeThresholdMinutes = severeThresholdMinutes(task.getRequestedMinutes());

        if (!referenceTime.isAfter(deadline)) {
            return new SubmissionDeadlineAssessment(
                    submitted
                            ? SubmissionDeadlineAssessment.Status.ON_TIME
                            : SubmissionDeadlineAssessment.Status.UPCOMING,
                    submitted,
                    0,
                    severeThresholdMinutes
            );
        }

        long lateMinutes = ceilingMinutes(Duration.between(deadline, referenceTime));
        if (!referenceTime.isAfter(deadline.plus(Duration.ofMinutes(GRACE_MINUTES)))) {
            return new SubmissionDeadlineAssessment(
                    SubmissionDeadlineAssessment.Status.GRACE,
                    submitted,
                    lateMinutes,
                    severeThresholdMinutes
            );
        }

        SubmissionDeadlineAssessment.Status status = referenceTime.isBefore(
                deadline.plus(Duration.ofMinutes(severeThresholdMinutes))
        )
                ? SubmissionDeadlineAssessment.Status.LATE
                : SubmissionDeadlineAssessment.Status.SEVERE;
        return new SubmissionDeadlineAssessment(status, submitted, lateMinutes, severeThresholdMinutes);
    }

    long severeThresholdMinutes(int requestedMinutes) {
        return Math.min(
                MAX_SEVERE_DELAY_MINUTES,
                Math.max(MIN_SEVERE_DELAY_MINUTES, requestedMinutes / 2L)
        );
    }

    private Instant firstSubmissionAt(Task task, Submission submission) {
        if (submission == null) {
            return null;
        }
        return submission.getCreatedAt() != null ? submission.getCreatedAt() : task.getSubmittedAt();
    }

    private long ceilingMinutes(Duration duration) {
        long seconds = duration.getSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }
}
