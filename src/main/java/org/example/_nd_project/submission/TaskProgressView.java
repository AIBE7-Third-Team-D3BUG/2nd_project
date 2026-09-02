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
        boolean cancelled,
        boolean disputed,
        int currentStep,
        List<String> completionCriteria,
        List<ActivityView> activities,
        SubmissionView submission,
        ReviewView review,
        boolean hasReferenceLink,
        boolean hasReferenceAttachment
) {
    public TaskProgressView(
            Long taskId, String title, String requesterName, String workerName, String deadlineLabel,
            int requestedMinutes, String statusLabel, boolean requester, boolean worker, boolean canStart,
            boolean waitingForWorkerStart, boolean canSubmit, boolean canReview, boolean completed,
            boolean cancelled, boolean disputed, int currentStep, List<String> completionCriteria,
            List<ActivityView> activities, SubmissionView submission, ReviewView review
    ) {
        this(taskId, title, requesterName, workerName, deadlineLabel, requestedMinutes, statusLabel,
                requester, worker, canStart, waitingForWorkerStart, canSubmit, canReview, completed,
                cancelled, disputed, currentStep, completionCriteria, activities, submission, review, false, false);
    }

    public boolean canCancel() {
        return requester && (currentStep == 2 || currentStep == 3) && !cancelled;
    }

    public boolean canOpenDispute() {
        return (requester || worker) && currentStep == 4 && !completed && !cancelled && !disputed;
    }

    public int requestedPum() {
        return requestedMinutes / 30;
    }

    public String phaseLabel() {
        if (completed) {
            return "BAROHAE · 업무 완료 / 정산";
        }
        if (cancelled) {
            return "BAROHAE · 업무 취소";
        }
        if (disputed) {
            return "BAROHAE · 문제 신고 / 검토";
        }
        if (canReview) {
            return "BAROHAE · 완료 승인 / 리뷰";
        }
        if (canStart) {
            return "BAROHAE · 업무 시작";
        }
        if (waitingForWorkerStart) {
            return "BAROHAE · 업무 시작 대기";
        }
        if (canSubmit) {
            return "BAROHAE · 업무 진행 / 결과 제출";
        }
        if (submission != null && worker) {
            return "BAROHAE · 완료 승인 대기";
        }
        return requester
                ? "BAROHAE · 업무 진행 확인"
                : "BAROHAE · 업무 진행 / 결과 제출";
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

    public record ReviewView(int rating, String content, Boolean deadlineMet) {
        public boolean hasContent() {
            return content != null && !content.isBlank();
        }

        public String ratingLabel() {
            return "★".repeat(rating) + "☆".repeat(5 - rating) + " " + rating + ".0";
        }
    }
}
