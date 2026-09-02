package org.example._nd_project;

import org.example._nd_project.notification.MemberNotificationService;
import org.example._nd_project.notification.MemberNotificationType;
import org.example._nd_project.notification.VolunteerSelectionNotificationService;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.volunteer.Volunteer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VolunteerSelectionNotificationServiceTest {

    @Mock MemberNotificationService notificationService;

    private VolunteerSelectionNotificationService service;

    @BeforeEach
    void setUp() {
        service = new VolunteerSelectionNotificationService(notificationService);
    }

    @Test
    void notSelectedApplicantReceivesSelectionResultOncePerSelectionEvent() {
        Task task = task();
        Volunteer rejected = volunteer(51L, 3L);
        Instant selectedAt = Instant.parse("2026-09-02T06:00:00Z");
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);

        service.notifyNotSelected(task, List.of(rejected), selectedAt);

        verify(notificationService).createIfAbsent(
                eq(3L),
                eq(MemberNotificationType.VOLUNTEER_NOT_SELECTED),
                eq("이번 업무의 작업자로 선정되지 않았습니다"),
                message.capture(),
                eq("/?view=detail&taskId=10"),
                eq("VOLUNTEER_NOT_SELECTED:10:"
                        + selectedAt.getEpochSecond() + "-" + selectedAt.getNano() + ":51")
        );
        assertTrue(message.getValue().contains("번역 검수"));
        assertTrue(message.getValue().contains("다른 지원자와 매칭"));
    }

    @Test
    void reopenedApplicantReceivesCorrectedStatusNotification() {
        Task task = task();
        Volunteer reopened = volunteer(51L, 3L);
        Instant reopenedAt = Instant.parse("2026-09-02T07:00:00Z");

        service.notifyReopened(task, List.of(reopened), reopenedAt);

        verify(notificationService).createIfAbsent(
                eq(3L),
                eq(MemberNotificationType.VOLUNTEER_REOPENED),
                eq("지원 상태가 다시 후보로 변경되었습니다"),
                eq("'번역 검수' 업무의 작업자 선택이 취소되어 다시 지원 후보 상태가 되었습니다."),
                eq("/?view=detail&taskId=10"),
                eq("VOLUNTEER_REOPENED:10:"
                        + reopenedAt.getEpochSecond() + "-" + reopenedAt.getNano() + ":51")
        );
    }

    private Task task() {
        Task task = Task.create(
                1L, "번역 검수", "설명", TaskCategory.TRANSLATION,
                new String[0], 60, Instant.now().plusSeconds(3_600),
                "검수 완료", null
        );
        ReflectionTestUtils.setField(task, "id", 10L);
        return task;
    }

    private Volunteer volunteer(Long id, Long memberId) {
        Volunteer volunteer = Volunteer.create(10L, memberId, "지원합니다.");
        ReflectionTestUtils.setField(volunteer, "id", id);
        return volunteer;
    }
}
