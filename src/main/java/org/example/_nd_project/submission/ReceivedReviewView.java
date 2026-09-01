package org.example._nd_project.submission;

import java.time.Instant;

public record ReceivedReviewView(
        Long taskId,
        String taskTitle,
        String reviewerNickname,
        int rating,
        String content,
        Boolean deadlineMet,
        Instant createdAt
) {
}
