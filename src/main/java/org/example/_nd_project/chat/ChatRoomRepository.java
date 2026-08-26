package org.example._nd_project.chat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<ChatRoom> {
    Optional<ChatRoom> findByTaskId(Long taskId);
    List<ChatRoom> findByRequesterMemberIdOrWorkerMemberIdOrderByLastMessageAtDescUpdatedAtDesc(Long requesterMemberId, Long workerMemberId);
    List<ChatRoom> findTop100ByOrderByLastMessageAtDescUpdatedAtDesc();
    @org.springframework.data.jpa.repository.Query("select room from ChatRoom room where room.requesterMemberId in :memberIds or room.workerMemberId in :memberIds order by room.lastMessageAt desc, room.updatedAt desc")
    List<ChatRoom> findByParticipantIds(
            @org.springframework.data.repository.query.Param("memberIds") java.util.Collection<Long> memberIds,
            org.springframework.data.domain.Pageable pageable);
}
