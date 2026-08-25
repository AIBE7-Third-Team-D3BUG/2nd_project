package org.example._nd_project.chat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByTaskId(Long taskId);
    List<ChatRoom> findByRequesterMemberIdOrWorkerMemberIdOrderByLastMessageAtDescUpdatedAtDesc(Long requesterMemberId, Long workerMemberId);
}
