package org.example._nd_project.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    List<ChatRoom> findByRequesterMemberIdOrWorkerMemberIdOrderByUpdatedAtDesc(Long requesterMemberId, Long workerMemberId);
    Optional<ChatRoom> findByTaskId(Long taskId);
    boolean existsByRequesterMemberIdOrWorkerMemberId(Long requesterMemberId, Long workerMemberId);
}
