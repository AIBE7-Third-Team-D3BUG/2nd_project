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
import java.util.Collection;
import org.springframework.data.domain.Page;

public interface TaskRepository extends JpaRepository<Task, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Task> {
    List<Task> findTop100ByOrderByCreatedAtDescIdDesc();
    Page<Task> findByRequesterIdInOrWorkerIdIn(Collection<Long> requesterIds,
                                               Collection<Long> workerIds, Pageable pageable);
    @Query("select task.id from Task task where task.requesterId in :memberIds or task.workerId in :memberIds")
    List<Long> findIdsByParticipantIds(@Param("memberIds") Collection<Long> memberIds);
    long countByStatus(TaskStatus status);
    List<Task> findByStatusAndDeadlineAtAfter(TaskStatus status, Instant now, Pageable pageable);
    List<Task> findByStatusAndCategoryAndDeadlineAtAfter(
            TaskStatus status, TaskCategory category, Instant now, Pageable pageable
    );
    List<Task> findByStatusAndDeadlineAtBefore(TaskStatus status, Instant now);
    List<Task> findByRequesterIdAndStatusNotOrderByCreatedAtDesc(Long requesterId, TaskStatus excludedStatus);
    List<Task> findByWorkerIdAndStatusNotOrderByUpdatedAtDesc(Long workerId, TaskStatus excludedStatus);
    List<Task> findByWorkerIdAndStatusInOrderByUpdatedAtDesc(Long workerId, List<TaskStatus> statuses);
    Optional<Task> findByIdAndRequesterId(Long id, Long requesterId);

    @Query("select count(t) > 0 from Task t where (t.requesterId = :memberId or t.workerId = :memberId) and t.status in :statuses")
    boolean existsActiveTaskByMemberId(@Param("memberId") Long memberId, @Param("statuses") java.util.Collection<TaskStatus> statuses);

    @Query("select t from Task t where (t.requesterId = :memberId or t.workerId = :memberId) and t.status in :statuses order by t.updatedAt desc")
    List<Task> findActiveTasksByMemberId(@Param("memberId") Long memberId, @Param("statuses") java.util.Collection<TaskStatus> statuses, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from Task task where task.id = :taskId")
    Optional<Task> findByIdForUpdate(@Param("taskId") Long taskId);
}
