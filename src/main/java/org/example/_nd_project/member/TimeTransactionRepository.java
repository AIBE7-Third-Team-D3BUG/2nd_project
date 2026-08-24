package org.example._nd_project.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeTransactionRepository extends JpaRepository<TimeTransaction, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("""
            select coalesce(sum(transaction.reservedDeltaMinutes), 0)
            from TimeTransaction transaction
            where transaction.taskId = :taskId
              and transaction.accountMemberId = :memberId
            """)
    long sumReservedMinutesByTaskAndMember(@Param("taskId") Long taskId,
                                           @Param("memberId") Long memberId);
}
