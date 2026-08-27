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

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(memberRepository, timeAccountRepository, timeTransactionRepository,
                taskRepository, disputeRepository, auditLogRepository, timeLedgerService, sessionRegistry);
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

        var dashboard = adminService.getDashboard(0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(memberRepository).findAll(pageable.capture());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals(1, dashboard.members().size());
        assertEquals(2, dashboard.memberTotalPages());
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
    void dashboardDateFilterUsesSpecificationsForEveryDatedSection() {
        when(memberRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Member>>any(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
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

        verify(memberRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Member>>any(),
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
}

