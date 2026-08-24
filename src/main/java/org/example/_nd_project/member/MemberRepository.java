package org.example._nd_project.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Member member set member.completedTaskCount = member.completedTaskCount + 1 where member.id = :memberId")
    int incrementCompletedTaskCount(@Param("memberId") Long memberId);
}
