package org.example._nd_project.admin;

import java.util.List;

public record AdminTaskProgressView(
        Long taskId, String title, String statusCode, String statusLabel, String category,
        String requesterName, String workerName, int requestedPum, String deadlineLabel,
        boolean overdue, List<TimelineRow> timeline, SubmissionRow submission,
        ReviewRow review, DisputeRow dispute, Long chatRoomId, long messageCount
) {
    public record TimelineRow(String step, String timeLabel, boolean completed, boolean current) {}
    public record SubmissionRow(String resultDescription, int actualPum, String requesterNote,
                                boolean hasResultFile, String createdAtLabel, String updatedAtLabel,
                                String deadlineLabel, long lateMinutes, boolean deadlineMet,
                                boolean severe, String deadlineAssessedAtLabel) {}
    public record ReviewRow(int rating, String content, Boolean deadlineMet, String createdAtLabel) {}
    public record DisputeRow(String status, String openedBy, String description,
                             String resolutionNote, String createdAtLabel, String resolvedAtLabel) {}
}
