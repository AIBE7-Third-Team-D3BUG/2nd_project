package org.example._nd_project.submission;

public record WorkerDelayMetrics(
        int windowDays,
        long submissionCount,
        long deadlineMetCount,
        long lateCount,
        long severeCount,
        int delayPoints
) {
    public static WorkerDelayMetrics empty(int windowDays) {
        return new WorkerDelayMetrics(windowDays, 0, 0, 0, 0, 0);
    }

    public boolean hasSamples() {
        return submissionCount > 0;
    }

    public long delayedCount() {
        return lateCount + severeCount;
    }

    public int deadlineMetPercent() {
        if (!hasSamples()) {
            return 0;
        }
        return (int) Math.round(deadlineMetCount * 100.0 / submissionCount);
    }

    public String statusLabel() {
        if (!hasSamples()) return "데이터 없음";
        if (delayPoints == 0) return "양호";
        if (delayPoints < 3) return "관찰";
        if (delayPoints < 5) return "주의";
        return "경고";
    }

    public String statusTone() {
        if (!hasSamples()) return "neutral";
        if (delayPoints == 0) return "success";
        if (delayPoints < 3) return "watch";
        if (delayPoints < 5) return "warning";
        return "danger";
    }
}
