package org.example._nd_project.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByStatusAndDeadlineAtAfter(TaskStatus status, Instant now, Pageable pageable);
    List<Task> findByStatusAndCategoryAndDeadlineAtAfter(
            TaskStatus status, TaskCategory category, Instant now, Pageable pageable
    );
    List<Task> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);
    Optional<Task> findByIdAndRequesterId(Long id, Long requesterId);
}
