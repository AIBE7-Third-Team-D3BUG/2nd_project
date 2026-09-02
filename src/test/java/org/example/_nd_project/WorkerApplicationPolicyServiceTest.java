package org.example._nd_project;

import org.example._nd_project.submission.SubmissionDeadlineAssessment;
import org.example._nd_project.submission.SubmissionRepository;
import org.example._nd_project.submission.WorkerDelayMetrics;
import org.example._nd_project.submission.WorkerDelayMetricsService;
import org.example._nd_project.volunteer.WorkerApplicationPolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerApplicationPolicyServiceTest {

    @Mock WorkerDelayMetricsService metricsService;
    @Mock SubmissionRepository submissionRepository;

    @Test
    void pointsBelowWarningThresholdRemainFullyEligible() {
        WorkerApplicationPolicyService service = service();
        when(metricsService.getForMember(2L)).thenReturn(metrics(2));

        var result = service.getEligibility(2L);

        assertTrue(result.canApply());
        assertFalse(result.warning());
        assertFalse(result.restricted());
        assertNull(result.recoveryAt());
        verify(submissionRepository, never()).findWorkerDelayEvents(eq(2L), any(Instant.class));
    }

    @Test
    void threePointsShowWarningButStillAllowApplications() {
        WorkerApplicationPolicyService service = service();
        Instant oldestLate = Instant.now().minus(Duration.ofDays(80));
        SubmissionRepository.WorkerDelayEvent lateEvent = event(
                SubmissionDeadlineAssessment.Status.LATE, oldestLate);
        when(metricsService.getForMember(2L)).thenReturn(metrics(3));
        when(submissionRepository.findWorkerDelayEvents(eq(2L), any(Instant.class)))
                .thenReturn(List.of(lateEvent));

        var result = service.getEligibility(2L);

        assertTrue(result.canApply());
        assertTrue(result.warning());
        assertFalse(result.restricted());
        assertEquals(oldestLate.plus(Duration.ofDays(90)), result.recoveryAt());
        assertDoesNotThrow(() -> service.requireCanApply(2L));
    }

    @Test
    void fivePointsRestrictApplicationsUntilOldestPointsExpire() {
        WorkerApplicationPolicyService service = service();
        Instant oldestSevere = Instant.now().minus(Duration.ofDays(80));
        SubmissionRepository.WorkerDelayEvent oldestSevereEvent = event(
                SubmissionDeadlineAssessment.Status.SEVERE, oldestSevere);
        when(metricsService.getForMember(2L)).thenReturn(metrics(5));
        when(submissionRepository.findWorkerDelayEvents(eq(2L), any(Instant.class)))
                .thenReturn(List.of(oldestSevereEvent));

        var result = service.getEligibility(2L);

        assertFalse(result.canApply());
        assertFalse(result.warning());
        assertTrue(result.restricted());
        assertEquals(oldestSevere.plus(Duration.ofDays(90)), result.recoveryAt());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.requireCanApply(2L));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    private WorkerApplicationPolicyService service() {
        return new WorkerApplicationPolicyService(metricsService, submissionRepository);
    }

    private WorkerDelayMetrics metrics(int points) {
        return new WorkerDelayMetrics(90, 5, 2, 1, 2, points);
    }

    private SubmissionRepository.WorkerDelayEvent event(SubmissionDeadlineAssessment.Status status,
                                                         Instant assessedAt) {
        return new SubmissionRepository.WorkerDelayEvent() {
            @Override
            public SubmissionDeadlineAssessment.Status getDeadlineStatus() {
                return status;
            }

            @Override
            public Instant getDeadlineAssessedAt() {
                return assessedAt;
            }
        };
    }
}
