package org.example._nd_project.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop30ByRoomIdOrderBySentAtAscIdAsc(Long roomId);
}
