package org.example._nd_project.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface DisputeRepository extends JpaRepository<Dispute, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Dispute> {
    boolean existsByTaskId(Long taskId);
    Optional<Dispute> findByTaskId(Long taskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dispute from Dispute dispute where dispute.id = :disputeId")
    Optional<Dispute> findByIdForUpdate(@Param("disputeId") Long disputeId);

    java.util.List<Dispute> findTop100ByOrderByCreatedAtDescIdDesc();
    java.util.List<Dispute> findTop100ByStatusInOrderByCreatedAtDescIdDesc(java.util.Collection<String> statuses);
    @org.springframework.data.jpa.repository.Query("select dispute from Dispute dispute where dispute.status in :statuses and (dispute.openedByMemberId in :memberIds or dispute.taskId in :taskIds) order by dispute.createdAt desc, dispute.id desc")
    java.util.List<Dispute> findPendingRelatedToMembers(
            @org.springframework.data.repository.query.Param("statuses") java.util.Collection<String> statuses,
            @org.springframework.data.repository.query.Param("memberIds") java.util.Collection<Long> memberIds,
            @org.springframework.data.repository.query.Param("taskIds") java.util.Collection<Long> taskIds,
            org.springframework.data.domain.Pageable pageable);
    long countByStatusIn(java.util.Collection<String> statuses);
}
