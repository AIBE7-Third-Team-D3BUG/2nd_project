package org.example._nd_project.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByStatusAndDeadlineAtAfter(TaskStatus status, Instant now, Pageable pageable);
    List<Task> findByStatusAndCategoryAndDeadlineAtAfter(
            TaskStatus status, TaskCategory category, Instant now, Pageable pageable
    );
    List<Task> findByRequesterIdAndStatusNotOrderByCreatedAtDesc(Long requesterId, TaskStatus excludedStatus);
    List<Task> findByWorkerIdAndStatusNotOrderByUpdatedAtDesc(Long workerId, TaskStatus excludedStatus);
    Optional<Task> findByIdAndRequesterId(Long id, Long requesterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from Task task where task.id = :taskId")
    Optional<Task> findByIdForUpdate(@Param("taskId") Long taskId);
}
