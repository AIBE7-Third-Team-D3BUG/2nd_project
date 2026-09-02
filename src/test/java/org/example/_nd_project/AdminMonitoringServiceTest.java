package org.example._nd_project;

import org.example._nd_project.admin.AdminAuditLog;
import org.example._nd_project.admin.AdminAuditLogRepository;
import org.example._nd_project.admin.AdminMonitoringService;
import org.example._nd_project.admin.AdminTaskProgressView;
import org.example._nd_project.chat.ChatMessage;
import org.example._nd_project.chat.ChatMessageRepository;
import org.example._nd_project.chat.ChatRoom;
import org.example._nd_project.chat.ChatRoomRepository;
import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.MemberRole;
import org.example._nd_project.submission.DisputeRepository;
import org.example._nd_project.submission.ReviewRepository;
import org.example._nd_project.submission.SubmissionRepository;
import org.example._nd_project.submission.Submission;
import org.example._nd_project.submission.SubmissionDeadlineAssessment;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStorageService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMonitoringServiceTest {
    @Mock MemberRepository memberRepository;
    @Mock TaskRepository taskRepository;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock SubmissionRepository submissionRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock DisputeRepository disputeRepository;
    @Mock AdminAuditLogRepository auditLogRepository;
    @Mock TaskStorageService taskStorageService;

    private AdminMonitoringService service;

    @BeforeEach
    void setUp() {
        service = new AdminMonitoringService(memberRepository, taskRepository, chatRoomRepository,
                chatMessageRepository, submissionRepository, reviewRepository, disputeRepository,
                auditLogRepository, taskStorageService);
    }

    @Test
    void adminBlindsMessageWithoutDeletingOriginalAndWritesAudit() {
        Member admin = member(1L, MemberRole.ADMIN);
        ChatRoom room = ChatRoom.create(10L, 2L, 3L, "업무");
        ReflectionTestUtils.setField(room, "id", 20L);
        ChatMessage message = ChatMessage.create(20L, 2L, "정책 위반 원문", null, null, null, null);
        ReflectionTestUtils.setField(message, "id", 30L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(chatMessageRepository.findById(30L)).thenReturn(Optional.of(message));
        when(chatRoomRepository.findById(20L)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findTopByRoomIdOrderBySentAtDescIdDesc(20L)).thenReturn(Optional.of(message));

        Long roomId = service.blindMessage(1L, 30L, "개인정보 노출");

        assertEquals(20L, roomId);
        assertTrue(message.isModerated());
        assertEquals("정책 위반 원문", message.getContent());
        assertEquals("관리자에 의해 블라인드된 메시지입니다.", room.getLastMessagePreview());
        verify(auditLogRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void adminCanInspectOpenTaskProgress() {
        Task task = Task.create(2L, "진행 확인", "설명", TaskCategory.DEVELOPMENT, new String[0],
                120, Instant.now().plusSeconds(3_600), "완료 기준", null);
        ReflectionTestUtils.setField(task, "id", 10L);
        ReflectionTestUtils.setField(task, "createdAt", Instant.now());
        Member requester = member(2L, MemberRole.USER);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(memberRepository.findAllById(List.of(2L))).thenReturn(List.of(requester));
        when(submissionRepository.findByTaskId(10L)).thenReturn(Optional.empty());
        when(reviewRepository.findByTaskId(10L)).thenReturn(Optional.empty());
        when(disputeRepository.findByTaskId(10L)).thenReturn(Optional.empty());
        when(chatRoomRepository.findByTaskId(10L)).thenReturn(Optional.empty());

        AdminTaskProgressView result = service.getTaskProgress(10L);

        assertEquals("OPEN", result.statusCode());
        assertEquals("모집 중", result.statusLabel());
        assertEquals(4, result.requestedPum());
        assertTrue(result.timeline().get(0).completed());
    }

    @Test
    void adminCanInspectPersistedLateSubmissionAssessment() {
        Instant submittedAt = Instant.now();
        Task task = Task.create(2L, "지연 제출 확인", "설명", TaskCategory.DEVELOPMENT, new String[0],
                120, submittedAt.minusSeconds(20 * 60), "완료 기준", null);
        ReflectionTestUtils.setField(task, "id", 10L);
        ReflectionTestUtils.setField(task, "workerId", 3L);
        ReflectionTestUtils.setField(task, "status", org.example._nd_project.task.TaskStatus.SUBMITTED);
        ReflectionTestUtils.setField(task, "submittedAt", submittedAt);
        ReflectionTestUtils.setField(task, "createdAt", submittedAt.minusSeconds(3_600));
        Submission submission = Submission.create(
                10L,
                3L,
                "지연 제출 결과",
                null,
                120,
                new SubmissionDeadlineAssessment(
                        SubmissionDeadlineAssessment.Status.LATE,
                        true,
                        20,
                        60
                ),
                submittedAt
        );
        ReflectionTestUtils.setField(submission, "createdAt", submittedAt);
        ReflectionTestUtils.setField(submission, "updatedAt", submittedAt);
        Member requester = member(2L, MemberRole.USER);
        Member worker = member(3L, MemberRole.USER);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(memberRepository.findAllById(List.of(2L, 3L))).thenReturn(List.of(requester, worker));
        when(submissionRepository.findByTaskId(10L)).thenReturn(Optional.of(submission));
        when(reviewRepository.findByTaskId(10L)).thenReturn(Optional.empty());
        when(disputeRepository.findByTaskId(10L)).thenReturn(Optional.empty());
        when(chatRoomRepository.findByTaskId(10L)).thenReturn(Optional.empty());

        AdminTaskProgressView result = service.getTaskProgress(10L);

        assertEquals(20, result.submission().lateMinutes());
        assertEquals("결과 제출이 20분 지연되고 있습니다.", result.submission().deadlineLabel());
        assertEquals(false, result.submission().deadlineMet());
        assertTrue(result.submission().penaltyEligible());
        assertFalse(result.submission().penaltyExempted());
    }

    @Test
    void adminCanExemptAndRestoreLateSubmissionPenaltyWithAuditTrail() {
        Instant submittedAt = Instant.now();
        Member admin = member(1L, MemberRole.ADMIN);
        Submission submission = Submission.create(
                10L,
                3L,
                "지연 제출 결과",
                null,
                120,
                new SubmissionDeadlineAssessment(
                        SubmissionDeadlineAssessment.Status.SEVERE,
                        true,
                        70,
                        60
                ),
                submittedAt
        );
        ReflectionTestUtils.setField(submission, "id", 40L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(submissionRepository.findByTaskIdForUpdate(10L)).thenReturn(Optional.of(submission));

        service.exemptSubmissionDelayPenalty(1L, 10L, "플랫폼 장애 확인");

        assertTrue(submission.isPenaltyExempted());
        assertEquals("플랫폼 장애 확인", submission.getPenaltyExemptionReason());
        assertEquals(1L, submission.getPenaltyExemptedBy());

        service.restoreSubmissionDelayPenalty(1L, 10L, "장애 시간과 제출 지연 시간이 다름");

        assertFalse(submission.isPenaltyExempted());
        assertNull(submission.getPenaltyExemptionReason());
        assertNull(submission.getPenaltyExemptedBy());
        assertNull(submission.getPenaltyExemptedAt());
        verify(auditLogRepository, times(2)).save(any(AdminAuditLog.class));
    }

    @Test
    void chatSearchReturnsOnlyRoomsForMatchedMemberIds() {
        when(memberRepository.findIdsByNicknameOrEmail("target@example.com")).thenReturn(List.of(2L));
        when(chatRoomRepository.findByParticipantIds(
                org.mockito.ArgumentMatchers.eq(java.util.Set.of(2L)),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of());

        var result = service.getChatRooms("target@example.com");

        assertTrue(result.rooms().isEmpty());
        verify(chatRoomRepository).findByParticipantIds(
                org.mockito.ArgumentMatchers.eq(java.util.Set.of(2L)),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
    }

    private Member member(Long id, MemberRole role) {
        Member member = Member.register("member" + id + "@example.com", "hash", "회원" + id, Instant.now());
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "role", role);
        return member;
    }
}
