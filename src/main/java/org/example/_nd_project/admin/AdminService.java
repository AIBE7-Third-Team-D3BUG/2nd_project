package org.example._nd_project.admin;

import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.MemberRole;
import org.example._nd_project.member.MemberStatus;
import org.example._nd_project.member.TimeAccount;
import org.example._nd_project.member.TimeAccountRepository;
import org.example._nd_project.member.TimeLedgerService;
import org.example._nd_project.member.TimeTransaction;
import org.example._nd_project.member.TimeTransactionRepository;
import org.example._nd_project.submission.Dispute;
import org.example._nd_project.submission.DisputeRepository;
import org.example._nd_project.submission.WorkerDelayMetrics;
import org.example._nd_project.submission.WorkerDelayMetricsService;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.session.SessionRegistry;
import org.example._nd_project.security.MemberPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.function.Function;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;

@Service
public class AdminService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final MemberRepository memberRepository;
    private final TimeAccountRepository timeAccountRepository;
    private final TimeTransactionRepository timeTransactionRepository;
    private final TaskRepository taskRepository;
    private final DisputeRepository disputeRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final TimeLedgerService timeLedgerService;
    private final SessionRegistry sessionRegistry;
    private final WorkerDelayMetricsService workerDelayMetricsService;

    public AdminService(MemberRepository memberRepository,
                        TimeAccountRepository timeAccountRepository,
                        TimeTransactionRepository timeTransactionRepository,
                        TaskRepository taskRepository,
                        DisputeRepository disputeRepository,
                        AdminAuditLogRepository auditLogRepository,
                        TimeLedgerService timeLedgerService,
                        SessionRegistry sessionRegistry,
                        WorkerDelayMetricsService workerDelayMetricsService) {
        this.memberRepository = memberRepository;
        this.timeAccountRepository = timeAccountRepository;
        this.timeTransactionRepository = timeTransactionRepository;
        this.taskRepository = taskRepository;
        this.disputeRepository = disputeRepository;
        this.auditLogRepository = auditLogRepository;
        this.timeLedgerService = timeLedgerService;
        this.sessionRegistry = sessionRegistry;
        this.workerDelayMetricsService = workerDelayMetricsService;
    }

    @Transactional(readOnly = true)
    public AdminDashboardView getDashboard() {
        return getDashboard(null, 0, 0, 0, 0);
    }

    @Transactional(readOnly = true)
    public AdminDashboardView getDashboard(int requestedMemberPage) {
        return getDashboard(null, requestedMemberPage, 0, 0, 0);
    }

    @Transactional(readOnly = true)
    public AdminDashboardView getDashboard(int requestedMemberPage, int requestedTaskPage,
                                           int requestedTransactionPage, int requestedAuditPage) {
        return getDashboard(null, requestedMemberPage, requestedTaskPage,
                requestedTransactionPage, requestedAuditPage);
    }

    @Transactional(readOnly = true)
    public AdminDashboardView getDashboard(String query, int requestedMemberPage, int requestedTaskPage,
                                           int requestedTransactionPage, int requestedAuditPage) {
        return getDashboard(query, null, requestedMemberPage, requestedTaskPage,
                requestedTransactionPage, requestedAuditPage);
    }

    @Transactional(readOnly = true)
    public AdminDashboardView getDashboard(String query, LocalDate date, int requestedMemberPage,
                                           int requestedTaskPage, int requestedTransactionPage,
                                           int requestedAuditPage) {
        String normalizedQuery = query == null ? "" : query.trim();
        boolean searching = !normalizedQuery.isBlank();
        boolean dateFiltering = date != null;
        Instant dateStart = dateFiltering ? date.atStartOfDay(KOREA).toInstant() : null;
        Instant dateEnd = dateFiltering ? date.plusDays(1).atStartOfDay(KOREA).toInstant() : null;
        List<Long> matchedMemberIds = searching
                ? memberRepository.findIdsByNicknameOrEmail(normalizedQuery) : List.of();
        Set<Long> queryMemberIds = matchedMemberIds.isEmpty() ? Set.of(-1L) : Set.copyOf(matchedMemberIds);
        List<Long> relatedTaskIds = searching && !matchedMemberIds.isEmpty()
                ? taskRepository.findIdsByParticipantIds(queryMemberIds) : List.of();
        Set<Long> queryTaskIds = relatedTaskIds.isEmpty() ? Set.of(-1L) : Set.copyOf(relatedTaskIds);

        Page<Member> memberResult = normalizedResult(requestedMemberPage, page -> searching
                ? memberRepository.findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        normalizedQuery, normalizedQuery, pageRequest(page, 20))
                : memberRepository.findAll(pageRequest(page, 20)));
        List<Member> members = memberResult.getContent();
        Page<Task> taskResult = normalizedResult(requestedTaskPage, page -> dateFiltering
                ? taskRepository.findAll(taskSpecification(searching, queryMemberIds, dateStart, dateEnd), pageRequest(page, 30))
                : searching ? taskRepository.findByRequesterIdInOrWorkerIdIn(queryMemberIds, queryMemberIds, pageRequest(page, 30))
                : taskRepository.findAll(pageRequest(page, 30)));
        List<Task> tasks = taskResult.getContent();
        List<String> pendingStatuses = List.of("OPEN", "UNDER_REVIEW");
        List<Dispute> disputes = dateFiltering
                ? disputeRepository.findAll(disputeSpecification(searching, queryMemberIds, queryTaskIds,
                        pendingStatuses, dateStart, dateEnd), PageRequest.of(0, 100)).getContent()
                : searching
                ? disputeRepository.findPendingRelatedToMembers(pendingStatuses, queryMemberIds, queryTaskIds,
                        PageRequest.of(0, 100))
                : disputeRepository.findTop100ByStatusInOrderByCreatedAtDescIdDesc(pendingStatuses);
        Set<Long> queryDisputeIds = disputes.isEmpty() ? Set.of(-1L)
                : disputes.stream().map(Dispute::getId).collect(java.util.stream.Collectors.toSet());
        Page<TimeTransaction> transactionResult = normalizedResult(requestedTransactionPage, page -> dateFiltering
                ? timeTransactionRepository.findAll(transactionSpecification(searching, queryMemberIds,
                        dateStart, dateEnd), pageRequest(page, 50))
                : searching ? timeTransactionRepository.findByAccountMemberIdIn(queryMemberIds, pageRequest(page, 50))
                : timeTransactionRepository.findAll(pageRequest(page, 50)));
        List<TimeTransaction> transactions = transactionResult.getContent();
        Page<AdminAuditLog> auditResult = normalizedResult(requestedAuditPage, page -> dateFiltering
                ? auditLogRepository.findAll(auditSpecification(searching, queryMemberIds, queryTaskIds,
                        queryDisputeIds, dateStart, dateEnd), pageRequest(page, 20))
                : searching ? auditLogRepository.findRelatedToMembers(queryMemberIds, queryTaskIds, queryDisputeIds,
                        pageRequest(page, 20))
                : auditLogRepository.findAll(pageRequest(page, 20)));
        List<AdminAuditLog> auditLogs = auditResult.getContent();

        Map<Long, Member> memberMap = new HashMap<>();
        memberRepository.findAll().forEach(member -> memberMap.put(member.getId(), member));
        Map<Long, TimeAccount> accountMap = new HashMap<>();
        timeAccountRepository.findAllById(members.stream().map(Member::getId).toList())
                .forEach(account -> accountMap.put(account.getMemberId(), account));
        Map<Long, WorkerDelayMetrics> delayMetricMap = workerDelayMetricsService.getForMembers(
                members.stream().map(Member::getId).toList());
        Map<Long, Task> taskMap = new HashMap<>();
        tasks.forEach(task -> taskMap.put(task.getId(), task));
        taskRepository.findAllById(disputes.stream().map(Dispute::getTaskId).distinct().toList())
                .forEach(task -> taskMap.putIfAbsent(task.getId(), task));

        return new AdminDashboardView(
                memberRepository.count(),
                memberRepository.countByStatus(MemberStatus.ACTIVE),
                taskRepository.countByStatus(TaskStatus.OPEN),
                disputeRepository.countByStatusIn(List.of("OPEN", "UNDER_REVIEW")),
                memberResult.getNumber(), memberResult.getTotalPages(),
                taskResult.getNumber(), taskResult.getTotalPages(),
                transactionResult.getNumber(), transactionResult.getTotalPages(),
                auditResult.getNumber(), auditResult.getTotalPages(),
                members.stream().map(member -> memberRow(
                        member,
                        accountMap.get(member.getId()),
                        delayMetricMap.getOrDefault(
                                member.getId(),
                                WorkerDelayMetrics.empty(WorkerDelayMetricsService.WINDOW_DAYS)
                        )
                )).toList(),
                tasks.stream().map(task -> taskRow(task, memberMap)).toList(),
                disputes.stream().map(dispute -> disputeRow(dispute, taskMap, memberMap)).toList(),
                transactions.stream().map(transaction -> transactionRow(transaction, memberMap)).toList(),
                auditLogs.stream().map(log -> auditRow(log, memberMap)).toList()
        );
    }

    private Specification<Task> taskSpecification(boolean userFilter, Set<Long> memberIds,
                                                    Instant start, Instant end) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = datePredicates(root.get("createdAt"), start, end, builder);
            if (userFilter) predicates.add(builder.or(root.get("requesterId").in(memberIds),
                    root.get("workerId").in(memberIds)));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<Dispute> disputeSpecification(boolean userFilter, Set<Long> memberIds,
                                                          Set<Long> taskIds, List<String> statuses,
                                                          Instant start, Instant end) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = datePredicates(root.get("createdAt"), start, end, builder);
            predicates.add(root.get("status").in(statuses));
            if (userFilter) predicates.add(builder.or(root.get("openedByMemberId").in(memberIds),
                    root.get("taskId").in(taskIds)));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<TimeTransaction> transactionSpecification(boolean userFilter, Set<Long> memberIds,
                                                                      Instant start, Instant end) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = datePredicates(root.get("createdAt"), start, end, builder);
            if (userFilter) predicates.add(root.get("accountMemberId").in(memberIds));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<AdminAuditLog> auditSpecification(boolean userFilter, Set<Long> memberIds,
                                                              Set<Long> taskIds, Set<Long> disputeIds,
                                                              Instant start, Instant end) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = datePredicates(root.get("createdAt"), start, end, builder);
            if (userFilter) predicates.add(builder.or(
                    root.get("adminMemberId").in(memberIds),
                    builder.and(builder.equal(root.get("targetType"), "MEMBER"), root.get("targetId").in(memberIds)),
                    builder.and(builder.equal(root.get("targetType"), "TASK"), root.get("targetId").in(taskIds)),
                    builder.and(builder.equal(root.get("targetType"), "DISPUTE"), root.get("targetId").in(disputeIds))));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<Predicate> datePredicates(jakarta.persistence.criteria.Path<Instant> path,
                                           Instant start, Instant end,
                                           jakarta.persistence.criteria.CriteriaBuilder builder) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.greaterThanOrEqualTo(path, start));
        predicates.add(builder.lessThan(path, end));
        return predicates;
    }

    private <T> Page<T> normalizedResult(int requestedPage, Function<Integer, Page<T>> loader) {
        int safePage = Math.max(0, requestedPage);
        Page<T> result = loader.apply(safePage);
        if (result.getTotalPages() > 0 && safePage >= result.getTotalPages()) {
            return loader.apply(result.getTotalPages() - 1);
        }
        return result;
    }

    private PageRequest pageRequest(int requestedPage, int size) {
        return PageRequest.of(Math.max(0, requestedPage), size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Transactional
    public void changeMemberStatus(Long adminId, Long memberId, MemberStatus status) {
        requireAdmin(adminId);
        if (adminId.equals(memberId)) {
            throw new IllegalArgumentException("현재 로그인한 관리자 계정의 상태는 변경할 수 없습니다.");
        }
        Member target = requireMember(memberId);
        if (target.getRole() == MemberRole.ADMIN) {
            throw new IllegalArgumentException("다른 관리자 계정의 상태는 이 화면에서 변경할 수 없습니다.");
        }
        target.changeStatus(status);
        if (status == MemberStatus.SUSPENDED) {
            expireMemberSessions(memberId);
        }
        audit(adminId, "MEMBER_STATUS_CHANGED", "MEMBER", memberId,
                target.getEmail() + " 상태를 " + status.name() + "(으)로 변경");
    }

    @Transactional
    public void adjustBalance(Long adminId, Long memberId, String operation, int pum, String reason) {
        requireAdmin(adminId);
        if (pum <= 0 || pum > 1_000) {
            throw new IllegalArgumentException("조정 재화는 1~1,000품 사이로 입력해주세요.");
        }
        String normalizedReason = requireReason(reason);
        TimeAccount account = timeAccountRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "시간 계정을 찾을 수 없습니다."));
        int minutes = Math.multiplyExact(pum, 30);
        int delta;
        if ("CREDIT".equalsIgnoreCase(operation)) {
            account.credit(minutes);
            delta = minutes;
        } else if ("DEBIT".equalsIgnoreCase(operation)) {
            account.debit(minutes);
            delta = -minutes;
        } else {
            throw new IllegalArgumentException("지원하지 않는 재화 조정 유형입니다.");
        }
        String transactionId = UUID.randomUUID().toString();
        timeTransactionRepository.save(TimeTransaction.adminAdjustment(
                memberId, delta, account.getAvailableMinutes(), account.getReservedMinutes(),
                adminId, transactionId, "관리자 조정: " + normalizedReason));
        audit(adminId, delta > 0 ? "BALANCE_CREDITED" : "BALANCE_DEBITED", "MEMBER", memberId,
                Math.abs(delta / 30) + "품 · " + normalizedReason);
    }

    @Transactional
    public void cancelOpenTask(Long adminId, Long taskId, String reason) {
        requireAdmin(adminId);
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다."));
        if (task.getStatus() != TaskStatus.OPEN) {
            throw new IllegalArgumentException("모집 중인 업무만 관리자 취소할 수 있습니다. 진행 업무는 분쟁 절차를 이용해주세요.");
        }
        String normalizedReason = requireReason(reason);
        timeLedgerService.refundTaskReservation(task.getRequesterId(), taskId, "관리자 업무 취소: " + normalizedReason);
        task.cancel(Instant.now());
        audit(adminId, "TASK_CANCELLED", "TASK", taskId, normalizedReason);
    }

    @Transactional
    public void startDisputeReview(Long adminId, Long disputeId) {
        requireAdmin(adminId);
        Dispute dispute = requireDisputeForUpdate(disputeId);
        dispute.startReview();
        audit(adminId, "DISPUTE_REVIEW_STARTED", "DISPUTE", disputeId,
                "업무 #" + dispute.getTaskId() + " 분쟁 검토 시작");
    }

    @Transactional
    public void resolveDispute(Long adminId, Long disputeId, boolean accepted, String note) {
        requireAdmin(adminId);
        String normalizedNote = requireReason(note);
        Dispute dispute = requireDisputeForUpdate(disputeId);
        Task task = taskRepository.findByIdForUpdate(dispute.getTaskId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "분쟁 대상 업무를 찾을 수 없습니다."));
        if (task.getStatus() != TaskStatus.DISPUTED) {
            throw new IllegalStateException("분쟁 대상 업무가 이미 처리되었거나 분쟁 상태가 아닙니다.");
        }

        if ("RESOLVED".equals(dispute.getStatus()) || "REJECTED".equals(dispute.getStatus())) {
            reconcileLegacyDisputeResolution(adminId, dispute, task, normalizedNote);
            return;
        }

        Instant resolvedAt = Instant.now();
        if (accepted) {
            timeLedgerService.refundTaskReservation(
                    task.getRequesterId(), task.getId(), "관리자 분쟁 승인에 따른 예약 재화 반환: " + normalizedNote
            );
        }
        task.resolveDispute(accepted, resolvedAt);
        dispute.resolve(accepted, normalizedNote, resolvedAt);
        audit(
                adminId,
                accepted ? "DISPUTE_RESOLVED" : "DISPUTE_REJECTED",
                "DISPUTE",
                disputeId,
                accepted
                        ? "업무 #" + task.getId() + " 취소 및 예약 재화 반환 · " + normalizedNote
                        : "업무 #" + task.getId() + " 결과 확인 상태 복귀 · " + normalizedNote
        );
    }

    private void reconcileLegacyDisputeResolution(Long adminId, Dispute dispute, Task task, String note) {
        boolean accepted = "RESOLVED".equals(dispute.getStatus());
        Instant resolvedAt = dispute.getResolvedAt() == null ? Instant.now() : dispute.getResolvedAt();
        if (accepted) {
            timeLedgerService.refundTaskReservation(
                    task.getRequesterId(), task.getId(), "기존 분쟁 처리 불일치 복구에 따른 예약 재화 반환: " + note
            );
        }
        task.resolveDispute(accepted, resolvedAt);
        audit(
                adminId,
                "DISPUTE_RECONCILED",
                "DISPUTE",
                dispute.getId(),
                accepted
                        ? "업무 #" + task.getId() + " 취소 및 예약 재화 반환 복구 · " + note
                        : "업무 #" + task.getId() + " 결과 확인 상태 복구 · " + note
        );
    }

    private AdminDashboardView.MemberRow memberRow(Member member, TimeAccount account,
                                                    WorkerDelayMetrics delayMetrics) {
        return new AdminDashboardView.MemberRow(
                member.getId(), member.getEmail(), member.getNickname(), member.getRole().name(), member.getStatus().name(),
                account == null ? 0 : account.getAvailableMinutes() / 30,
                account == null ? 0 : account.getReservedMinutes() / 30,
                format(member.getCreatedAt()),
                delayMetrics.delayPoints(), delayMetrics.lateCount(), delayMetrics.severeCount(),
                delayMetrics.deadlineMetPercent(), delayMetrics.submissionCount(),
                delayMetrics.statusLabel(), delayMetrics.statusTone());
    }

    private AdminDashboardView.TaskRow taskRow(Task task, Map<Long, Member> members) {
        return new AdminDashboardView.TaskRow(
                task.getId(), task.getTitle(), memberName(members, task.getRequesterId()),
                task.getWorkerId() == null ? "-" : memberName(members, task.getWorkerId()),
                task.getStatus().getLabel(), task.getRequestedMinutes() / 30,
                deadlineLabel(task.getDeadlineAt()), task.getStatus() == TaskStatus.OPEN);
    }

    private AdminDashboardView.DisputeRow disputeRow(Dispute dispute, Map<Long, Task> tasks,
                                                       Map<Long, Member> members) {
        Task task = tasks.get(dispute.getTaskId());
        return new AdminDashboardView.DisputeRow(
                dispute.getId(), dispute.getTaskId(), task == null ? "업무 #" + dispute.getTaskId() : task.getTitle(),
                memberName(members, dispute.getOpenedByMemberId()), dispute.getDescription(), dispute.getStatus(),
                format(dispute.getCreatedAt()));
    }

    private AdminDashboardView.TransactionRow transactionRow(TimeTransaction transaction, Map<Long, Member> members) {
        return new AdminDashboardView.TransactionRow(
                transaction.getId(), memberName(members, transaction.getAccountMemberId()),
                transaction.getTransactionType(), transaction.getAvailableDeltaMinutes() / 30,
                transaction.getAvailableBalanceAfter() / 30, transaction.getReason(), format(transaction.getCreatedAt()));
    }

    private AdminDashboardView.AuditRow auditRow(AdminAuditLog log, Map<Long, Member> members) {
        return new AdminDashboardView.AuditRow(
                log.getId(), memberName(members, log.getAdminMemberId()), log.getAction(),
                log.getTargetType() + " #" + log.getTargetId(), log.getDetails(), format(log.getCreatedAt()));
    }

    private Member requireAdmin(Long adminId) {
        Member admin = requireMember(adminId);
        if (admin.getRole() != MemberRole.ADMIN || admin.getStatus() != MemberStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
        return admin;
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    private Dispute requireDispute(Long disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "분쟁을 찾을 수 없습니다."));
    }

    private Dispute requireDisputeForUpdate(Long disputeId) {
        return disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "분쟁을 찾을 수 없습니다."));
    }

    private void audit(Long adminId, String action, String targetType, Long targetId, String details) {
        String safeDetails = details.length() > 1_000 ? details.substring(0, 1_000) : details;
        auditLogRepository.save(AdminAuditLog.create(adminId, action, targetType, targetId, safeDetails));
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("처리 사유를 입력해주세요.");
        }
        String normalized = reason.trim();
        if (normalized.length() > 450) {
            throw new IllegalArgumentException("처리 사유는 450자 이하로 입력해주세요.");
        }
        return normalized;
    }

    private String memberName(Map<Long, Member> members, Long memberId) {
        Member member = members.get(memberId);
        return member == null ? "회원 #" + memberId : member.getNickname();
    }

    private String format(Instant instant) {
        return instant == null ? "-" : DATE_TIME.format(instant.atZone(KOREA));
    }

    private String deadlineLabel(Instant deadline) {
        if (deadline == null) return "-";
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative()) return "마감";
        long minutes = remaining.toMinutes();
        if (minutes < 60) return minutes + "분 남음";
        if (minutes < 1_440) return (minutes / 60) + "시간 남음";
        return format(deadline);
    }

    private void expireMemberSessions(Long memberId) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(MemberPrincipal.class::isInstance)
                .map(MemberPrincipal.class::cast)
                .filter(principal -> memberId.equals(principal.memberId()))
                .forEach(principal -> sessionRegistry.getAllSessions(principal, false)
                        .forEach(session -> session.expireNow()));
    }
}
