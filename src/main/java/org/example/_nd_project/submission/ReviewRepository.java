package org.example._nd_project.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByTaskId(Long taskId);
    boolean existsByTaskId(Long taskId);
}
