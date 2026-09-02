package org.example._nd_project;

import org.example._nd_project.submission.SubmissionRepository;
import org.example._nd_project.submission.WorkerDelayMetrics;
import org.example._nd_project.submission.WorkerDelayMetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerDelayMetricsServiceTest {

    @Mock SubmissionRepository submissionRepository;

    @Test
    void aggregatesRecentSubmissionAssessmentsIntoPointsAndComplianceRate() {
        WorkerDelayMetricsService service = new WorkerDelayMetricsService(submissionRepository);
        SubmissionRepository.WorkerDelayMetric metric = mock(SubmissionRepository.WorkerDelayMetric.class);
        when(metric.getWorkerId()).thenReturn(7L);
        when(metric.getSubmissionCount()).thenReturn(5L);
        when(metric.getDeadlineMetCount()).thenReturn(3L);
        when(metric.getLateCount()).thenReturn(1L);
        when(metric.getSevereCount()).thenReturn(1L);
        when(metric.getDelayPoints()).thenReturn(3L);
        when(submissionRepository.findWorkerDelayMetrics(anyCollection(), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(metric));
        Instant before = Instant.now().minus(Duration.ofDays(90)).minusSeconds(2);

        WorkerDelayMetrics result = service.getForMember(7L);

        assertEquals(5, result.submissionCount());
        assertEquals(60, result.deadlineMetPercent());
        assertEquals(3, result.delayPoints());
        assertEquals("주의", result.statusLabel());
        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(submissionRepository).findWorkerDelayMetrics(anyCollection(), since.capture());
        assertTrue(since.getValue().isAfter(before));
    }

    @Test
    void suppliesZeroMetricsForMembersWithoutRecentSubmissions() {
        WorkerDelayMetricsService service = new WorkerDelayMetricsService(submissionRepository);
        when(submissionRepository.findWorkerDelayMetrics(anyCollection(), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of());

        WorkerDelayMetrics result = service.getForMember(8L);

        assertEquals(0, result.submissionCount());
        assertEquals(0, result.deadlineMetPercent());
        assertEquals("데이터 없음", result.statusLabel());
    }

    @Test
    void skipsRepositoryForEmptyMemberCollection() {
        WorkerDelayMetricsService service = new WorkerDelayMetricsService(submissionRepository);

        assertTrue(service.getForMembers(List.of()).isEmpty());
        verify(submissionRepository, never()).findWorkerDelayMetrics(
                anyCollection(), org.mockito.ArgumentMatchers.any(Instant.class));
    }
}
