package org.example._nd_project.notification;

import org.example._nd_project.submission.Submission;
import org.example._nd_project.submission.SubmissionDeadlineAssessment;
import org.example._nd_project.submission.WorkerDelayMetrics;
import org.example._nd_project.submission.WorkerDelayMetricsService;
import org.example._nd_project.volunteer.WorkerApplicationPolicyService;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class DelayPenaltyNotificationService {

    private final WorkerDelayMetricsService metricsService;
    private final MemberNotificationService notificationService;

    public DelayPenaltyNotificationService(WorkerDelayMetricsService metricsService,
                                           MemberNotificationService notificationService) {
        this.metricsService = metricsService;
        this.notificationService = notificationService;
    }

    public void notifyFirstDelay(Submission submission) {
        SubmissionDeadlineAssessment assessment = submission.getDeadlineAssessment();
        int addedPoints = pointsFor(assessment.status());
        if (addedPoints == 0) return;

        WorkerDelayMetrics metrics = metricsService.getForMember(submission.getWorkerId());
        int currentPoints = metrics.delayPoints();
        int previousPoints = Math.max(0, currentPoints - addedPoints);
        MemberNotificationType type;
        String title;
        if (currentPoints >= WorkerApplicationPolicyService.RESTRICTION_THRESHOLD) {
            type = MemberNotificationType.APPLICATION_RESTRICTED;
            title = previousPoints < WorkerApplicationPolicyService.RESTRICTION_THRESHOLD
                    ? "신규 업무 지원이 제한되었습니다"
                    : "지원 제한 상태에서 지연 점수가 추가되었습니다";
        } else if (currentPoints >= WorkerApplicationPolicyService.WARNING_THRESHOLD) {
            type = MemberNotificationType.DELAY_WARNING;
            title = previousPoints < WorkerApplicationPolicyService.WARNING_THRESHOLD
                    ? "지연 점수가 주의 구간에 도달했습니다"
                    : "지연 점수가 추가 반영되었습니다";
        } else {
            type = MemberNotificationType.DELAY_RECORDED;
            title = "제출 지연 점수가 반영되었습니다";
        }

        String message = "업무 #" + submission.getTaskId() + " 결과가 "
                + assessment.lateMinutes() + "분 지연으로 판정되어 " + addedPoints
                + "점이 반영되었습니다. 현재 최근 90일 지연 점수는 " + currentPoints + "점입니다.";
        notificationService.createIfAbsent(
                submission.getWorkerId(), type, title, message,
                taskProgressUrl(submission), "DELAY_SUBMISSION:" + submission.getId());
    }

    public void notifyPenaltyExempted(Submission submission, String reason) {
        int removedPoints = pointsFor(submission.getDeadlineAssessment().status());
        WorkerDelayMetrics metrics = metricsService.getForMember(submission.getWorkerId());
        int currentPoints = metrics.delayPoints();
        int previousPoints = currentPoints + removedPoints;
        boolean restrictionLifted = previousPoints >= WorkerApplicationPolicyService.RESTRICTION_THRESHOLD
                && currentPoints < WorkerApplicationPolicyService.RESTRICTION_THRESHOLD;
        String title = restrictionLifted
                ? "신규 업무 지원 제한이 해제되었습니다"
                : "제출 지연 패널티가 면제되었습니다";
        String message = "업무 #" + submission.getTaskId() + " 지연 패널티 " + removedPoints
                + "점이 관리자에 의해 면제되었습니다. 현재 점수는 " + currentPoints
                + "점입니다. 사유: " + reason;
        notificationService.createIfAbsent(
                submission.getWorkerId(), MemberNotificationType.PENALTY_EXEMPTED,
                title, message, taskProgressUrl(submission),
                "PENALTY_EXEMPTED:" + submission.getId() + ":"
                        + submission.getPenaltyExemptedAt().toEpochMilli());
    }

    public void notifyPenaltyRestored(Submission submission, String reason, Instant restoredAt) {
        int restoredPoints = pointsFor(submission.getDeadlineAssessment().status());
        WorkerDelayMetrics metrics = metricsService.getForMember(submission.getWorkerId());
        int currentPoints = metrics.delayPoints();
        int previousPoints = Math.max(0, currentPoints - restoredPoints);
        boolean restrictionStarted = previousPoints < WorkerApplicationPolicyService.RESTRICTION_THRESHOLD
                && currentPoints >= WorkerApplicationPolicyService.RESTRICTION_THRESHOLD;
        String title = restrictionStarted
                ? "신규 업무 지원이 다시 제한되었습니다"
                : "제출 지연 패널티가 다시 적용되었습니다";
        String message = "업무 #" + submission.getTaskId() + " 지연 패널티 " + restoredPoints
                + "점이 다시 적용되었습니다. 현재 점수는 " + currentPoints
                + "점입니다. 사유: " + reason;
        notificationService.createIfAbsent(
                submission.getWorkerId(), MemberNotificationType.PENALTY_RESTORED,
                title, message, taskProgressUrl(submission),
                "PENALTY_RESTORED:" + submission.getId() + ":" + restoredAt.toEpochMilli());
    }

    private int pointsFor(SubmissionDeadlineAssessment.Status status) {
        if (status == SubmissionDeadlineAssessment.Status.SEVERE) return 2;
        if (status == SubmissionDeadlineAssessment.Status.LATE) return 1;
        return 0;
    }

    private String taskProgressUrl(Submission submission) {
        return "/tasks/" + submission.getTaskId() + "/progress";
    }
}
