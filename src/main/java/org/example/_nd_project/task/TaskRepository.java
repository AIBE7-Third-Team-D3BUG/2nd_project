package org.example._nd_project.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findTop20ByStatusAndDeadlineAtAfterOrderByCreatedAtDesc(TaskStatus status, Instant now);
    List<Task> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);
    Optional<Task> findByIdAndRequesterId(Long id, Long requesterId);
}
