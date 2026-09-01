package org.example._nd_project.submission;

import java.time.Instant;

public record WrittenReviewView(
        Long taskId,
        String taskTitle,
        String revieweeNickname,
        int rating,
        String content,
        Boolean deadlineMet,
        Instant createdAt
) {
}
