package org.example._nd_project.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeTransactionRepository extends JpaRepository<TimeTransaction, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<TimeTransaction> {
    java.util.List<TimeTransaction> findTop100ByOrderByCreatedAtDescIdDesc();
    boolean existsByIdempotencyKey(String idempotencyKey);
    org.springframework.data.domain.Page<TimeTransaction> findByAccountMemberIdIn(
            java.util.Collection<Long> memberIds, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select coalesce(sum(transaction.reservedDeltaMinutes), 0)
            from TimeTransaction transaction
            where transaction.taskId = :taskId
              and transaction.accountMemberId = :memberId
            """)
    long sumReservedMinutesByTaskAndMember(@Param("taskId") Long taskId,
                                           @Param("memberId") Long memberId);
}
