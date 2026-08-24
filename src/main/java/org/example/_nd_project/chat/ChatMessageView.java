package org.example._nd_project.chat;

import java.time.Instant;

public record ChatMessageView(
        Long id,
        Long senderId,
        String senderNickname,
        String content,
        String attachmentName,
        String attachmentPath,
        Long attachmentSize,
        Instant sentAt,
        boolean mine
) {
}
