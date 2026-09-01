package org.example._nd_project.submission;

public record SubmissionDeadlineAssessment(
        Status status,
        boolean submitted,
        long lateMinutes,
        long severeThresholdMinutes
) {

    public enum Status {
        UPCOMING,
        ON_TIME,
        GRACE,
        LATE,
        SEVERE
    }

    public boolean deadlineMet() {
        return submitted && (status == Status.ON_TIME || status == Status.GRACE);
    }

    public boolean overdue() {
        return status == Status.LATE || status == Status.SEVERE;
    }

    public boolean severe() {
        return status == Status.SEVERE;
    }

    public boolean visible() {
        return status != Status.UPCOMING;
    }

    public String label() {
        return switch (status) {
            case UPCOMING -> "";
            case ON_TIME -> "마감 전에 결과를 제출했어요.";
            case GRACE -> submitted
                    ? "10분 제출 유예 안에 결과를 제출했어요."
                    : "마감 직후 10분 제출 유예 중입니다.";
            case LATE -> "결과 제출이 " + lateMinutes + "분 지연되고 있습니다.";
            case SEVERE -> "심각한 제출 지연 · " + lateMinutes + "분 지연";
        };
    }

    public String tone() {
        if (deadlineMet()) {
            return "success";
        }
        if (severe()) {
            return "danger";
        }
        return "warning";
    }
}
