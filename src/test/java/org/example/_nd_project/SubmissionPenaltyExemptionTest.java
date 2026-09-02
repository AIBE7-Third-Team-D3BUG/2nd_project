package org.example._nd_project;

import org.example._nd_project.submission.Submission;
import org.example._nd_project.submission.SubmissionDeadlineAssessment;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionPenaltyExemptionTest {

    @Test
    void exemptionPreservesOriginalDelayAssessmentAndCanBeRestored() {
        Instant assessedAt = Instant.parse("2026-09-01T01:00:00Z");
        Submission submission = submission(SubmissionDeadlineAssessment.Status.SEVERE, 75, assessedAt);

        submission.exemptDelayPenalty(9L, "서비스 장애 확인", assessedAt.plusSeconds(300));

        assertTrue(submission.isPenaltyExempted());
        assertEquals("서비스 장애 확인", submission.getPenaltyExemptionReason());
        assertEquals(9L, submission.getPenaltyExemptedBy());
        assertEquals(SubmissionDeadlineAssessment.Status.SEVERE,
                submission.getDeadlineAssessment().status());
        assertEquals(75, submission.getDeadlineAssessment().lateMinutes());

        submission.restoreDelayPenalty();

        assertFalse(submission.isPenaltyExempted());
        assertNull(submission.getPenaltyExemptionReason());
        assertNull(submission.getPenaltyExemptedBy());
        assertNull(submission.getPenaltyExemptedAt());
        assertEquals(SubmissionDeadlineAssessment.Status.SEVERE,
                submission.getDeadlineAssessment().status());
    }

    @Test
    void onTimeSubmissionCannotReceivePenaltyExemption() {
        Submission submission = submission(
                SubmissionDeadlineAssessment.Status.ON_TIME,
                0,
                Instant.parse("2026-09-01T01:00:00Z")
        );

        assertThrows(IllegalStateException.class,
                () -> submission.exemptDelayPenalty(9L, "면제 불필요", Instant.now()));
    }

    private Submission submission(SubmissionDeadlineAssessment.Status status,
                                  long lateMinutes,
                                  Instant assessedAt) {
        return Submission.create(
                10L,
                3L,
                "결과",
                null,
                120,
                new SubmissionDeadlineAssessment(status, true, lateMinutes, 60),
                assessedAt
        );
    }
}
