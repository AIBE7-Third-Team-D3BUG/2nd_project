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

    public String phaseLabel() {
        if (completed) {
            return "COMMON-05 · 업무 완료 / 정산";
        }
        if (disputed) {
            return "COMMON-04 · 문제 신고 / 검토";
        }
        if (canReview) {
            return "CLIENT-04 · 완료 승인 / 리뷰";
        }
        if (canStart) {
            return "COMMON-03 · 업무 시작";
        }
        if (waitingForWorkerStart) {
            return "COMMON-03 · 업무 시작 대기";
        }
        if (canSubmit) {
            return "COMMON-03 · 업무 진행 / 결과 제출";
        }
        if (submission != null && worker) {
            return "COMMON-03 · 완료 승인 대기";
        }
        return requester
                ? "COMMON-03 · 업무 진행 확인"
                : "COMMON-03 · 업무 진행 / 결과 제출";
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
