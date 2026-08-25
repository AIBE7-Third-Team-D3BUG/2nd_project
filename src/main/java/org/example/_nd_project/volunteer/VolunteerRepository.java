package org.example._nd_project.volunteer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VolunteerRepository extends JpaRepository<Volunteer, Long> {
    boolean existsByTaskIdAndMemberId(Long taskId, Long memberId);
    boolean existsByTaskIdAndMemberIdAndStatusNot(Long taskId, Long memberId, VolunteerStatus status);
    boolean existsByTaskIdAndMemberIdAndStatusIn(Long taskId, Long memberId, java.util.Collection<VolunteerStatus> statuses);
    long countByTaskIdAndStatus(Long taskId, VolunteerStatus status);
    long countByTaskId(Long taskId);
    List<Volunteer> findByTaskIdOrderByCreatedAtAsc(Long taskId);
    List<Volunteer> findByTaskIdAndStatusNotOrderByCreatedAtAsc(Long taskId, VolunteerStatus status);
    List<Volunteer> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<Volunteer> findByMemberIdAndStatusOrderByCreatedAtDesc(Long memberId, VolunteerStatus status);
    List<Volunteer> findByMemberIdAndStatusNotOrderByCreatedAtDesc(Long memberId, VolunteerStatus status);
    Optional<Volunteer> findByIdAndTaskId(Long id, Long taskId);
    Optional<Volunteer> findByTaskIdAndMemberId(Long taskId, Long memberId);
}