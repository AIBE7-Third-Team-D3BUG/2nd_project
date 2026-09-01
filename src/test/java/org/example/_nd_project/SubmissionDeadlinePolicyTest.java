package org.example._nd_project;

import org.example._nd_project.submission.Submission;
import org.example._nd_project.submission.SubmissionDeadlineAssessment;
import org.example._nd_project.submission.SubmissionDeadlinePolicy;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskCategory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionDeadlinePolicyTest {

    private final SubmissionDeadlinePolicy policy = new SubmissionDeadlinePolicy();
    private final Instant deadline = Instant.parse("2026-09-01T03:00:00Z");

    @Test
    void upcomingDeadlineWithoutSubmissionIsHidden() {
        SubmissionDeadlineAssessment assessment = policy.assess(
                task(30),
                null,
                deadline.minusSeconds(1)
        );

        assertEquals(SubmissionDeadlineAssessment.Status.UPCOMING, assessment.status());
        assertFalse(assessment.visible());
        assertFalse(assessment.deadlineMet());
    }

    @Test
    void submissionAtDeadlineIsOnTime() {
        SubmissionDeadlineAssessment assessment = policy.assess(
                task(30),
                submissionAt(deadline),
                deadline.plusSeconds(3_600)
        );

        assertEquals(SubmissionDeadlineAssessment.Status.ON_TIME, assessment.status());
        assertTrue(assessment.deadlineMet());
    }

    @Test
    void submissionAssessmentTreatsProvidedTimeAsCompletedSubmission() {
        SubmissionDeadlineAssessment assessment = policy.assessAtSubmission(
                task(30),
                deadline
        );

        assertEquals(SubmissionDeadlineAssessment.Status.ON_TIME, assessment.status());
        assertTrue(assessment.submitted());
        assertTrue(assessment.deadlineMet());
    }

    @Test
    void submissionAtEndOfGracePeriodMeetsDeadline() {
        SubmissionDeadlineAssessment assessment = policy.assess(
                task(30),
                submissionAt(deadline.plusSeconds(10 * 60)),
                deadline.plusSeconds(3_600)
        );

        assertEquals(SubmissionDeadlineAssessment.Status.GRACE, assessment.status());
        assertEquals(10, assessment.lateMinutes());
        assertTrue(assessment.deadlineMet());
    }

    @Test
    void submissionImmediatelyAfterGracePeriodIsLate() {
        SubmissionDeadlineAssessment assessment = policy.assess(
                task(120),
                submissionAt(deadline.plusSeconds(10 * 60 + 1)),
                deadline.plusSeconds(3_600)
        );

        assertEquals(SubmissionDeadlineAssessment.Status.LATE, assessment.status());
        assertEquals(11, assessment.lateMinutes());
        assertFalse(assessment.deadlineMet());
    }

    @Test
    void onePumTaskBecomesSeverelyLateAfterThirtyMinutes() {
        SubmissionDeadlineAssessment assessment = policy.assess(
                task(30),
                null,
                deadline.plusSeconds(30 * 60)
        );

        assertEquals(30, assessment.severeThresholdMinutes());
        assertEquals(SubmissionDeadlineAssessment.Status.SEVERE, assessment.status());
        assertTrue(assessment.severe());
    }

    @Test
    void fourPumTaskUsesHalfOfRequestedTimeAsSevereThreshold() {
        SubmissionDeadlineAssessment beforeThreshold = policy.assess(
                task(120),
                null,
                deadline.plusSeconds(60 * 60 - 1)
        );
        SubmissionDeadlineAssessment atThreshold = policy.assess(
                task(120),
                null,
                deadline.plusSeconds(60 * 60)
        );

        assertEquals(60, atThreshold.severeThresholdMinutes());
        assertEquals(SubmissionDeadlineAssessment.Status.LATE, beforeThreshold.status());
        assertEquals(SubmissionDeadlineAssessment.Status.SEVERE, atThreshold.status());
    }

    @Test
    void longTaskCapsSevereThresholdAtTwoHours() {
        SubmissionDeadlineAssessment assessment = policy.assess(
                task(480),
                null,
                deadline.plusSeconds(120 * 60)
        );

        assertEquals(120, assessment.severeThresholdMinutes());
        assertEquals(SubmissionDeadlineAssessment.Status.SEVERE, assessment.status());
    }

    private Task task(int requestedMinutes) {
        return Task.create(
                3L,
                "마감 정책 테스트",
                "테스트 업무",
                TaskCategory.DEVELOPMENT,
                new String[]{"Spring"},
                requestedMinutes,
                deadline,
                "테스트 결과",
                (String) null
        );
    }

    private Submission submissionAt(Instant submittedAt) {
        Submission submission = Submission.create(
                10L,
                8L,
                "결과",
                null,
                30,
                new SubmissionDeadlineAssessment(
                        SubmissionDeadlineAssessment.Status.ON_TIME,
                        true,
                        0,
                        30
                ),
                submittedAt
        );
        ReflectionTestUtils.setField(submission, "createdAt", submittedAt);
        return submission;
    }
}
