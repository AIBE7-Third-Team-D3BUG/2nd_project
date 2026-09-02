package org.example._nd_project.notification;

import org.example._nd_project.task.Task;
import org.example._nd_project.volunteer.Volunteer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class VolunteerSelectionNotificationService {

    private final MemberNotificationService notificationService;

    public VolunteerSelectionNotificationService(MemberNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void notifyNotSelected(Task task, List<Volunteer> rejectedVolunteers, Instant selectedAt) {
        for (Volunteer volunteer : rejectedVolunteers) {
            notificationService.createIfAbsent(
                    volunteer.getMemberId(),
                    MemberNotificationType.VOLUNTEER_NOT_SELECTED,
                    "이번 업무의 작업자로 선정되지 않았습니다",
                    "'" + task.getTitle() + "' 업무는 다른 지원자와 매칭되었습니다. 지원해주셔서 감사합니다.",
                    taskDetailUrl(task),
                    eventKey("VOLUNTEER_NOT_SELECTED", task, volunteer, selectedAt)
            );
        }
    }

    public void notifyReopened(Task task, List<Volunteer> reopenedVolunteers, Instant reopenedAt) {
        for (Volunteer volunteer : reopenedVolunteers) {
            notificationService.createIfAbsent(
                    volunteer.getMemberId(),
                    MemberNotificationType.VOLUNTEER_REOPENED,
                    "지원 상태가 다시 후보로 변경되었습니다",
                    "'" + task.getTitle() + "' 업무의 작업자 선택이 취소되어 다시 지원 후보 상태가 되었습니다.",
                    taskDetailUrl(task),
                    eventKey("VOLUNTEER_REOPENED", task, volunteer, reopenedAt)
            );
        }
    }

    private String eventKey(String eventType, Task task, Volunteer volunteer, Instant eventAt) {
        String volunteerKey = volunteer.getId() == null
                ? "member-" + volunteer.getMemberId()
                : volunteer.getId().toString();
        return eventType + ":" + task.getId() + ":"
                + eventAt.getEpochSecond() + "-" + eventAt.getNano() + ":" + volunteerKey;
    }

    private String taskDetailUrl(Task task) {
        return "/?view=detail&taskId=" + task.getId();
    }
}
