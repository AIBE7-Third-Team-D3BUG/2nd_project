package org.example._nd_project.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface TimeAccountRepository extends JpaRepository<TimeAccount, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from TimeAccount account where account.memberId = :memberId")
    Optional<TimeAccount> findByMemberIdForUpdate(@Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from TimeAccount account where account.memberId in :memberIds order by account.memberId")
    List<TimeAccount> findAllByMemberIdInForUpdate(@Param("memberIds") List<Long> memberIds);
}
