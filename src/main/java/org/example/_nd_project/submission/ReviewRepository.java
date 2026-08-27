package org.example._nd_project.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    interface DeadlineMetric {
        Long getMemberId();
        long getMetCount();
        long getSampleCount();
    }

    Optional<Review> findByTaskId(Long taskId);
    boolean existsByTaskId(Long taskId);

    @Query("""
            select review.revieweeId as memberId,
                   sum(case when review.deadlineMet = true then 1 else 0 end) as metCount,
                   count(review) as sampleCount
            from Review review
            where review.revieweeId in :memberIds
              and review.deadlineMet is not null
            group by review.revieweeId
            """)
    List<DeadlineMetric> findDeadlineMetrics(@Param("memberIds") Collection<Long> memberIds);
}
