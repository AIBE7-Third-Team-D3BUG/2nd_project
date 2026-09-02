package org.example._nd_project;

import org.example._nd_project.submission.SubmissionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@Transactional(readOnly = true)
@Tag("supabase")
class WorkerDelayMetricsRepositorySupabaseTest {

    @Autowired SubmissionRepository submissionRepository;

    @Test
    void recentWorkerDelayAggregateQueryExecutesAgainstPostgresql() {
        var result = submissionRepository.findWorkerDelayMetrics(
                List.of(-1L), Instant.now().minus(Duration.ofDays(90)));

        assertThat(result).isEmpty();
    }
}
