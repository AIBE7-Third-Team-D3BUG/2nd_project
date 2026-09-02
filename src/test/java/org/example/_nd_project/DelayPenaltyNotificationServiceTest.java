package org.example._nd_project;

import org.example._nd_project.notification.DelayPenaltyNotificationService;
import org.example._nd_project.notification.MemberNotificationService;
import org.example._nd_project.notification.MemberNotificationType;
import org.example._nd_project.submission.Submission;
import org.example._nd_project.submission.SubmissionDeadlineAssessment;
import org.example._nd_project.submission.WorkerDelayMetrics;
import org.example._nd_project.submission.WorkerDelayMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DelayPenaltyNotificationServiceTest {

    @Mock WorkerDelayMetricsService metricsService;
    @Mock MemberNotificationService notificationService;

    private DelayPenaltyNotificationService service;

    @BeforeEach
    void setUp() {
        service = new DelayPenaltyNotificationService(metricsService, notificationService);
    }

    @Test
    void firstDelayCrossingFivePointsCreatesRestrictionNotification() {
        Submission submission = severeSubmission();
        when(metricsService.getForMember(3L)).thenReturn(metrics(5));
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);

        service.notifyFirstDelay(submission);

        verify(notificationService).createIfAbsent(
                eq(3L), eq(MemberNotificationType.APPLICATION_RESTRICTED),
                title.capture(), message.capture(), eq("/tasks/10/progress"),
                eq("DELAY_SUBMISSION:40"));
        assertEquals("신규 업무 지원이 제한되었습니다", title.getValue());
        assertTrue(message.getValue().contains("2점이 반영"));
        assertTrue(message.getValue().contains("현재 최근 90일 지연 점수는 5점"));
    }

    @Test
    void exemptionDroppingBelowFivePointsCreatesRestrictionLiftedNotification() {
        Submission submission = severeSubmission();
        Instant exemptedAt = Instant.parse("2026-09-02T03:00:00Z");
        submission.exemptDelayPenalty(1L, "서비스 장애", exemptedAt);
        when(metricsService.getForMember(3L)).thenReturn(metrics(4));
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);

        service.notifyPenaltyExempted(submission, "서비스 장애");

        verify(notificationService).createIfAbsent(
                eq(3L), eq(MemberNotificationType.PENALTY_EXEMPTED),
                title.capture(), anyString(), eq("/tasks/10/progress"),
                eq("PENALTY_EXEMPTED:40:" + exemptedAt.toEpochMilli()));
        assertEquals("신규 업무 지원 제한이 해제되었습니다", title.getValue());
    }

    @Test
    void restoredPenaltyCrossingFivePointsCreatesRestrictionNotification() {
        Submission submission = severeSubmission();
        Instant restoredAt = Instant.parse("2026-09-02T04:00:00Z");
        when(metricsService.getForMember(3L)).thenReturn(metrics(5));
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);

        service.notifyPenaltyRestored(submission, "면제 근거 불충분", restoredAt);

        verify(notificationService).createIfAbsent(
                eq(3L), eq(MemberNotificationType.PENALTY_RESTORED),
                title.capture(), anyString(), eq("/tasks/10/progress"),
                eq("PENALTY_RESTORED:40:" + restoredAt.toEpochMilli()));
        assertEquals("신규 업무 지원이 다시 제한되었습니다", title.getValue());
    }

    private Submission severeSubmission() {
        Submission submission = Submission.create(
                10L,
                3L,
                "결과",
                null,
                120,
                new SubmissionDeadlineAssessment(
                        SubmissionDeadlineAssessment.Status.SEVERE,
                        true,
                        70,
                        60
                ),
                Instant.parse("2026-09-02T02:00:00Z")
        );
        ReflectionTestUtils.setField(submission, "id", 40L);
        return submission;
    }

    private WorkerDelayMetrics metrics(int points) {
        return new WorkerDelayMetrics(90, 5, 2, 1, 2, points);
    }
}
