package org.example._nd_project.volunteer;

import org.example._nd_project.submission.SubmissionDeadlineAssessment;
import org.example._nd_project.submission.SubmissionRepository;
import org.example._nd_project.submission.WorkerDelayMetrics;
import org.example._nd_project.submission.WorkerDelayMetricsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class WorkerApplicationPolicyService {

    public static final int WARNING_THRESHOLD = 3;
    public static final int RESTRICTION_THRESHOLD = 5;

    private final WorkerDelayMetricsService metricsService;
    private final SubmissionRepository submissionRepository;

    public WorkerApplicationPolicyService(WorkerDelayMetricsService metricsService,
                                          SubmissionRepository submissionRepository) {
        this.metricsService = metricsService;
        this.submissionRepository = submissionRepository;
    }

    @Transactional(readOnly = true)
    public WorkerApplicationEligibility getEligibility(Long memberId) {
        WorkerDelayMetrics metrics = metricsService.getForMember(memberId);
        int points = metrics.delayPoints();
        boolean restricted = points >= RESTRICTION_THRESHOLD;
        boolean warning = !restricted && points >= WARNING_THRESHOLD;
        if (!restricted && !warning) {
            return new WorkerApplicationEligibility(metrics, false, false, null);
        }

        Instant now = Instant.now();
        List<SubmissionRepository.WorkerDelayEvent> events = submissionRepository.findWorkerDelayEvents(
                memberId, now.minus(Duration.ofDays(WorkerDelayMetricsService.WINDOW_DAYS)));
        int targetThreshold = restricted ? RESTRICTION_THRESHOLD : WARNING_THRESHOLD;
        Instant recoveryAt = calculateRecoveryAt(points, targetThreshold, events);
        return new WorkerApplicationEligibility(metrics, warning, restricted, recoveryAt);
    }

    public void requireCanApply(Long memberId) {
        WorkerApplicationEligibility eligibility = getEligibility(memberId);
        if (eligibility.restricted()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, eligibility.restrictionMessage());
        }
    }

    private Instant calculateRecoveryAt(int currentPoints, int targetThreshold,
                                        List<SubmissionRepository.WorkerDelayEvent> events) {
        int remainingPoints = currentPoints;
        for (SubmissionRepository.WorkerDelayEvent event : events) {
            remainingPoints -= pointsFor(event.getDeadlineStatus());
            if (remainingPoints < targetThreshold && event.getDeadlineAssessedAt() != null) {
                return event.getDeadlineAssessedAt().plus(Duration.ofDays(WorkerDelayMetricsService.WINDOW_DAYS));
            }
        }
        return null;
    }

    private int pointsFor(SubmissionDeadlineAssessment.Status status) {
        if (status == SubmissionDeadlineAssessment.Status.SEVERE) return 2;
        if (status == SubmissionDeadlineAssessment.Status.LATE) return 1;
        return 0;
    }
}
