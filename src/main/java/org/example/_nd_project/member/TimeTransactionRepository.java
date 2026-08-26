package org.example._nd_project.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TimeTransactionRepository extends JpaRepository<TimeTransaction, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    List<TimeTransaction> findByAccountMemberIdOrderByCreatedAtDesc(Long accountMemberId, Pageable pageable);

    long countByAccountMemberId(Long accountMemberId);

    @Query("""
            select coalesce(sum(transaction.reservedDeltaMinutes), 0)
            from TimeTransaction transaction
            where transaction.taskId = :taskId
              and transaction.accountMemberId = :memberId
            """)
    long sumReservedMinutesByTaskAndMember(@Param("taskId") Long taskId,
                                           @Param("memberId") Long memberId);
}
