package org.example._nd_project.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Pageable;

public interface MemberRepository extends JpaRepository<Member, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Member> {
    List<Member> findTop100ByOrderByCreatedAtDescIdDesc();
    Page<Member> findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String nickname, String email, Pageable pageable);
    @Query("select member.id from Member member where lower(member.nickname) like lower(concat('%', :query, '%')) or lower(member.email) like lower(concat('%', :query, '%'))")
    List<Long> findIdsByNicknameOrEmail(@Param("query") String query);
    @Query("select member from Member member where lower(member.nickname) like lower(concat('%', :query, '%')) or lower(member.email) like lower(concat('%', :query, '%')) order by member.createdAt desc, member.id desc")
    List<Member> searchByNicknameOrEmail(@Param("query") String query, Pageable pageable);
    long countByStatus(MemberStatus status);
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Member member set member.completedTaskCount = member.completedTaskCount + 1 where member.id = :memberId")
    int incrementCompletedTaskCount(@Param("memberId") Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Member member
            set member.completedTaskCount = member.completedTaskCount + 1,
                member.reviewCount = member.reviewCount + 1,
                member.ratingSum = member.ratingSum + :rating
            where member.id = :memberId
            """)
    int recordCompletedTaskReview(@Param("memberId") Long memberId, @Param("rating") int rating);
}
