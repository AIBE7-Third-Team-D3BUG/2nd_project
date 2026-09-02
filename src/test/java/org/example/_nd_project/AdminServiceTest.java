package org.example._nd_project;

import org.example._nd_project.admin.AdminAuditLog;
import org.example._nd_project.admin.AdminAuditLogRepository;
import org.example._nd_project.admin.AdminService;
import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.MemberRole;
import org.example._nd_project.member.MemberStatus;
import org.example._nd_project.member.TimeAccount;
import org.example._nd_project.member.TimeAccountRepository;
import org.example._nd_project.member.TimeLedgerService;
import org.example._nd_project.member.TimeTransaction;
import org.example._nd_project.member.TimeTransactionRepository;
import org.example._nd_project.submission.DisputeRepository;
import org.example._nd_project.submission.Dispute;
import org.example._nd_project.submission.WorkerDelayMetrics;
import org.example._nd_project.submission.WorkerDelayMetricsService;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionInformation;
import org.example._nd_project.security.MemberPrincipal;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    @Mock MemberRepository memberRepository;
    @Mock TimeAccountRepository timeAccountRepository;
    @Mock TimeTransactionRepository timeTransactionRepository;
    @Mock TaskRepository taskRepository;
    @Mock DisputeRepository disputeRepository;
    @Mock AdminAuditLogRepository auditLogRepository;
    @Mock TimeLedgerService timeLedgerService;
    @Mock SessionRegistry sessionRegistry;
    @Mock WorkerDelayMetricsService workerDelayMetricsService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(memberRepository, timeAccountRepository, timeTransactionRepository,
                taskRepository, disputeRepository, auditLogRepository, timeLedgerService, sessionRegistry,
                workerDelayMetricsService);
    }

    @Test
    void adminCanCreditBalanceAndWritesBothLedgers() {
        Member admin = member(1L, "admin@example.com", MemberRole.ADMIN);
        Member user = member(2L, "user@example.com", MemberRole.USER);
        TimeAccount account = new TimeAccount(2L, 120);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(timeAccountRepository.findByMemberIdForUpdate(2L)).thenReturn(Optional.of(account));

        adminService.adjustBalance(1L, 2L, "CREDIT", 3, "이벤트 보상");

        assertEquals(210, account.getAvailableMinutes());
        ArgumentCaptor<TimeTransaction> transaction = ArgumentCaptor.forClass(TimeTransaction.class);
        verify(timeTransactionRepository).save(transaction.capture());
        assertEquals("ADMIN_CREDIT", transaction.getValue().getTransactionType());
        assertEquals(90, transaction.getValue().getAvailableDeltaMinutes());
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.any(AdminAuditLog.class));
    }

    @Test
    void dashboardLoadsMembersTwentyAtATime() {
        Member user = member(2L, "user@example.com", MemberRole.USER);
        when(memberRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(user), invocation.getArgument(0), 21));
        when(taskRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 0));
        when(timeTransactionRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 0));
        when(auditLogRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 0));
        when(workerDelayMetricsService.getForMembers(List.of(2L))).thenReturn(Map.of(
                2L, new WorkerDelayMetrics(90, 4, 2, 1, 1, 3)
        ));

        var dashboard = adminService.getDashboard(0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(memberRepository).findAll(pageable.capture());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals(1, dashboard.members().size());
        assertEquals(2, dashboard.memberTotalPages());
        assertEquals(3, dashboard.members().get(0).delayPoints());
        assertEquals("주의", dashboard.members().get(0).delayStatusLabel());
        verify(disputeRepository).findTop100ByStatusInOrderByCreatedAtDescIdDesc(
                List.of("OPEN", "UNDER_REVIEW"));
    }

    @Test
    void dashboardUsesRequestedPageSizesForManagementSections() {
        when(memberRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 0));
        when(taskRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 0));
        when(timeTransactionRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 0));
        when(auditLogRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 0));

        adminService.getDashboard(0, 0, 0, 0);

        ArgumentCaptor<Pageable> taskPage = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Pageable> transactionPage = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Pageable> auditPage = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).findAll(taskPage.capture());
        verify(timeTransactionRepository).findAll(transactionPage.capture());
        verify(auditLogRepository).findAll(auditPage.capture());
        assertEquals(30, taskPage.getValue().getPageSize());
        assertEquals(50, transactionPage.getValue().getPageSize());
        assertEquals(20, auditPage.getValue().getPageSize());
    }

    @Test
    void dashboardSearchUsesMatchedMemberForEveryManagementSection() {
        Member user = member(2L, "target@example.com", MemberRole.USER);
        when(memberRepository.findIdsByNicknameOrEmail("target")).thenReturn(List.of(2L));
        when(taskRepository.findIdsByParticipantIds(java.util.Set.of(2L))).thenReturn(List.of(10L));
        when(memberRepository.findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                org.mockito.ArgumentMatchers.eq("target"), org.mockito.ArgumentMatchers.eq("target"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(user), invocation.getArgument(2), 1));
        when(taskRepository.findByRequesterIdInOrWorkerIdIn(
                org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(2), 0));
        when(disputeRepository.findPendingRelatedToMembers(
                org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of());
        when(timeTransactionRepository.findByAccountMemberIdIn(
                org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
        when(auditLogRepository.findRelatedToMembers(
                org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(3), 0));

        var dashboard = adminService.getDashboard("target", 0, 0, 0, 0);

        assertEquals(1, dashboard.members().size());
        assertEquals("target@example.com", dashboard.members().get(0).email());
        verify(timeTransactionRepository).findByAccountMemberIdIn(
                org.mockito.ArgumentMatchers.eq(java.util.Set.of(2L)), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void dashboardDateFilterExcludesMemberManagementAndFiltersDatedSections() {
        when(memberRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 0));
        when(taskRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Task>>any(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
        when(disputeRepository.findAll(org.mockito.ArgumentMatchers.<Specification<org.example._nd_project.submission.Dispute>>any(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
        when(timeTransactionRepository.findAll(org.mockito.ArgumentMatchers.<Specification<TimeTransaction>>any(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
        when(auditLogRepository.findAll(org.mockito.ArgumentMatchers.<Specification<AdminAuditLog>>any(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));

        adminService.getDashboard("", LocalDate.of(2026, 8, 26), 0, 0, 0, 0);

        verify(memberRepository).findAll(org.mockito.ArgumentMatchers.any(Pageable.class));
        verify(memberRepository, org.mockito.Mockito.never()).findAll(
                org.mockito.ArgumentMatchers.<Specification<Member>>any(),
                org.mockito.ArgumentMatchers.any(Pageable.class));
        verify(taskRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Task>>any(),
                org.mockito.ArgumentMatchers.any(Pageable.class));
        verify(timeTransactionRepository).findAll(org.mockito.ArgumentMatchers.<Specification<TimeTransaction>>any(),
                org.mockito.ArgumentMatchers.any(Pageable.class));
        verify(auditLogRepository).findAll(org.mockito.ArgumentMatchers.<Specification<AdminAuditLog>>any(),
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void normalUserCannotExecuteAdminAction() {
        Member user = member(2L, "user@example.com", MemberRole.USER);
        when(memberRepository.findById(2L)).thenReturn(Optional.of(user));
        assertThrows(ResponseStatusException.class,
                () -> adminService.adjustBalance(2L, 2L, "CREDIT", 1, "부당 요청"));
    }

    @Test
    void suspendingMemberExpiresExistingSessions() {
        Member admin = member(1L, "admin@example.com", MemberRole.ADMIN);
        Member user = member(2L, "user@example.com", MemberRole.USER);
        MemberPrincipal principal = MemberPrincipal.from(user);
        SessionInformation session = org.mockito.Mockito.mock(SessionInformation.class);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(user));
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(principal));
        when(sessionRegistry.getAllSessions(principal, false)).thenReturn(List.of(session));

        adminService.changeMemberStatus(1L, 2L, MemberStatus.SUSPENDED);

        assertEquals(MemberStatus.SUSPENDED, user.getStatus());
        verify(session).expireNow();
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.any(AdminAuditLog.class));
    }

    @Test
    void adminCancelsOnlyOpenTaskAndRefundsReservation() {
        Member admin = member(1L, "admin@example.com", MemberRole.ADMIN);
        Task task = Task.create(2L, "업무", "설명", TaskCategory.DEVELOPMENT, new String[0], 60,
                Instant.now().plusSeconds(3600), "완료 기준", null);
        ReflectionTestUtils.setField(task, "id", 10L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));

        adminService.cancelOpenTask(1L, 10L, "정책 위반");

        verify(timeLedgerService).refundTaskReservation(2L, 10L, "관리자 업무 취소: 정책 위반");
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.any(AdminAuditLog.class));
    }

    @Test
    void acceptedDisputeCancelsTaskAndRefundsRequesterReservation() {
        Member admin = member(1L, "admin@example.com", MemberRole.ADMIN);
        Task task = disputedTask();
        Dispute dispute = Dispute.open(10L, 2L, "결과물이 완료 기준과 다릅니다.");
        ReflectionTestUtils.setField(dispute, "id", 30L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(disputeRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(dispute));
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));

        adminService.resolveDispute(1L, 30L, true, "요청자에게 예약 품을 반환합니다.");

        assertEquals("RESOLVED", dispute.getStatus());
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        verify(timeLedgerService).refundTaskReservation(
                2L, 10L, "관리자 분쟁 승인에 따른 예약 재화 반환: 요청자에게 예약 품을 반환합니다."
        );
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.any(AdminAuditLog.class));
    }

    @Test
    void rejectedDisputeReturnsTaskToSubmittedWithoutRefund() {
        Member admin = member(1L, "admin@example.com", MemberRole.ADMIN);
        Task task = disputedTask();
        Dispute dispute = Dispute.open(10L, 2L, "결과물이 완료 기준과 다릅니다.");
        ReflectionTestUtils.setField(dispute, "id", 30L);
        dispute.startReview();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(disputeRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(dispute));
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));

        adminService.resolveDispute(1L, 30L, false, "제출 결과를 다시 확인하도록 합니다.");

        assertEquals("REJECTED", dispute.getStatus());
        assertEquals(TaskStatus.SUBMITTED, task.getStatus());
        verify(timeLedgerService, org.mockito.Mockito.never()).refundTaskReservation(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void legacyResolvedDisputeIsReconciledAndRefunded() {
        Member admin = member(1L, "admin@example.com", MemberRole.ADMIN);
        Task task = disputedTask();
        Dispute dispute = Dispute.open(10L, 2L, "결과물이 완료 기준과 다릅니다.");
        ReflectionTestUtils.setField(dispute, "id", 30L);
        dispute.resolve(true, "기존 관리자 처리", Instant.parse("2026-08-26T00:47:52Z"));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(disputeRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(dispute));
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));

        adminService.resolveDispute(1L, 30L, true, "기존 불일치 데이터 복구");

        assertEquals("RESOLVED", dispute.getStatus());
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        verify(timeLedgerService).refundTaskReservation(
                2L, 10L, "기존 분쟁 처리 불일치 복구에 따른 예약 재화 반환: 기존 불일치 데이터 복구"
        );
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.any(AdminAuditLog.class));
    }

    @Test
    void adminCannotSuspendAnotherAdmin() {
        Member admin = member(1L, "admin@example.com", MemberRole.ADMIN);
        Member targetAdmin = member(3L, "other-admin@example.com", MemberRole.ADMIN);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(memberRepository.findById(3L)).thenReturn(Optional.of(targetAdmin));

        assertThrows(IllegalArgumentException.class,
                () -> adminService.changeMemberStatus(1L, 3L, MemberStatus.SUSPENDED));
    }

    private Member member(Long id, String email, MemberRole role) {
        Member member = Member.register(email, "hash", "회원" + id, Instant.now());
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "role", role);
        return member;
    }

    private Task disputedTask() {
        Task task = Task.create(2L, "분쟁 업무", "설명", TaskCategory.DEVELOPMENT, new String[0], 60,
                Instant.now().plusSeconds(3600), "완료 기준", null);
        ReflectionTestUtils.setField(task, "id", 10L);
        ReflectionTestUtils.setField(task, "workerId", 3L);
        ReflectionTestUtils.setField(task, "status", TaskStatus.DISPUTED);
        return task;
    }
}
