package org.example._nd_project.submission;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

@Service
public class WorkerDelayMetricsService {

    public static final int WINDOW_DAYS = 90;

    private final SubmissionRepository submissionRepository;

    public WorkerDelayMetricsService(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Transactional(readOnly = true)
    public WorkerDelayMetrics getForMember(Long memberId) {
        if (memberId == null) {
            return empty();
        }
        return getForMembers(java.util.List.of(memberId)).getOrDefault(memberId, empty());
    }

    @Transactional(readOnly = true)
    public Map<Long, WorkerDelayMetrics> getForMembers(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        memberIds.stream().filter(java.util.Objects::nonNull).forEach(uniqueIds::add);
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, WorkerDelayMetrics> result = new LinkedHashMap<>();
        uniqueIds.forEach(memberId -> result.put(memberId, empty()));
        Instant since = Instant.now().minus(Duration.ofDays(WINDOW_DAYS));
        for (SubmissionRepository.WorkerDelayMetric metric
                : submissionRepository.findWorkerDelayMetrics(uniqueIds, since)) {
            if (metric.getWorkerId() == null) {
                continue;
            }
            result.put(metric.getWorkerId(), new WorkerDelayMetrics(
                    WINDOW_DAYS,
                    value(metric.getSubmissionCount()),
                    value(metric.getDeadlineMetCount()),
                    value(metric.getLateCount()),
                    value(metric.getSevereCount()),
                    Math.toIntExact(value(metric.getDelayPoints()))
            ));
        }
        return Map.copyOf(result);
    }

    public WorkerDelayMetrics empty() {
        return WorkerDelayMetrics.empty(WINDOW_DAYS);
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }
}
