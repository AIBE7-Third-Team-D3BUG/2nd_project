package org.example._nd_project.submission;

import java.util.List;

public record TaskProgressView(
        Long taskId,
        String title,
        String requesterName,
        String workerName,
        String deadlineLabel,
        int requestedMinutes,
        String statusLabel,
        boolean requester,
        boolean worker,
        boolean canStart,
        boolean waitingForWorkerStart,
        boolean canSubmit,
        boolean canReview,
        boolean completed,
        boolean disputed,
        int currentStep,
        List<String> completionCriteria,
        List<ActivityView> activities,
        SubmissionView submission
) {
    public int requestedPum() {
        return requestedMinutes / 30;
    }

    public record ActivityView(String title, String description, String timeLabel, boolean current) {
    }

    public record SubmissionView(
            String resultDescription,
            boolean hasResultAsset,
            String resultAssetLabel,
            String submittedAtLabel,
            String requesterNote
    ) {
        public boolean hasRequesterNote() {
            return requesterNote != null && !requesterNote.isBlank();
        }
    }
}
