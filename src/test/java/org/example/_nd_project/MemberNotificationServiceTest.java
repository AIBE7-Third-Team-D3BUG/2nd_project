package org.example._nd_project;

import org.example._nd_project.notification.MemberNotification;
import org.example._nd_project.notification.MemberNotificationRepository;
import org.example._nd_project.notification.MemberNotificationService;
import org.example._nd_project.notification.MemberNotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberNotificationServiceTest {

    @Mock MemberNotificationRepository notificationRepository;

    private MemberNotificationService service;

    @BeforeEach
    void setUp() {
        service = new MemberNotificationService(notificationRepository);
    }

    @Test
    void returnsLatestNotificationsWithUnreadCount() {
        MemberNotification notification = notification();
        ReflectionTestUtils.setField(notification, "id", 7L);
        ReflectionTestUtils.setField(notification, "createdAt", Instant.parse("2026-09-02T03:00:00Z"));
        when(notificationRepository.findTop20ByMemberIdOrderByCreatedAtDescIdDesc(3L))
                .thenReturn(List.of(notification));
        when(notificationRepository.countByMemberIdAndReadAtIsNull(3L)).thenReturn(1L);

        var center = service.getCenter(3L);

        assertEquals(1, center.unreadCount());
        assertEquals("신규 업무 지원이 제한되었습니다", center.notifications().get(0).title());
        assertEquals("danger", center.notifications().get(0).tone());
        assertEquals("2026.09.02 12:00", center.notifications().get(0).createdAtLabel());
    }

    @Test
    void openingOwnedNotificationMarksItReadAndReturnsSafeTarget() {
        MemberNotification notification = notification();
        when(notificationRepository.findByIdAndMemberId(7L, 3L)).thenReturn(Optional.of(notification));

        String target = service.markReadAndGetTarget(3L, 7L);

        assertEquals("/tasks/10/progress", target);
        assertTrue(notification.isRead());
    }

    @Test
    void duplicateEventKeyDoesNotCreateAnotherNotification() {
        when(notificationRepository.existsByEventKey("DELAY_SUBMISSION:40")).thenReturn(true);

        service.createIfAbsent(
                3L, MemberNotificationType.DELAY_RECORDED,
                "제출 지연", "1점 반영", "/tasks/10/progress", "DELAY_SUBMISSION:40");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void marksEveryUnreadNotificationAsRead() {
        service.markAllRead(3L);

        verify(notificationRepository).markAllRead(
                org.mockito.ArgumentMatchers.eq(3L), any(Instant.class));
    }

    private MemberNotification notification() {
        return MemberNotification.create(
                3L,
                MemberNotificationType.APPLICATION_RESTRICTED,
                "신규 업무 지원이 제한되었습니다",
                "최근 90일 지연 점수는 5점입니다.",
                "/tasks/10/progress",
                "DELAY_SUBMISSION:40"
        );
    }
}
