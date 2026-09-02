package org.example._nd_project.volunteer;

import org.example._nd_project.submission.WorkerDelayMetrics;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record WorkerApplicationEligibility(
        WorkerDelayMetrics metrics,
        boolean warning,
        boolean restricted,
        Instant recoveryAt
) {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter RECOVERY_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    public boolean canApply() {
        return !restricted;
    }

    public String recoveryAtLabel() {
        if (recoveryAt == null) {
            return "지연 기록 만료 시 자동 재계산";
        }
        return RECOVERY_FORMAT.format(recoveryAt.atZone(KOREA));
    }

    public String restrictionMessage() {
        return "최근 90일 지연 점수가 " + metrics.delayPoints()
                + "점으로 신규 업무 지원이 제한됩니다. 예상 해제: " + recoveryAtLabel();
    }
}
