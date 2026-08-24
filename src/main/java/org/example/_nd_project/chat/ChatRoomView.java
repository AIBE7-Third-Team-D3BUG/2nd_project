package org.example._nd_project.chat;

import java.time.Instant;
import java.util.List;

public record ChatRoomView(
        Long id,
        Long taskId,
        String taskTitle,
        String taskStatus,
        String participantName,
        String lastMessagePreview,
        Instant lastMessageAt,
        List<ChatMessageView> messages
) {
}
