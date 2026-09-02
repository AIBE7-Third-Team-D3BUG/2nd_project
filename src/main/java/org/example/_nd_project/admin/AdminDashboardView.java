package org.example._nd_project.admin;

import java.util.List;

public record AdminDashboardView(
        long totalMembers, long activeMembers, long openTasks, long pendingDisputes,
        int memberPage, int memberTotalPages,
        int taskPage, int taskTotalPages,
        int transactionPage, int transactionTotalPages,
        int auditPage, int auditTotalPages,
        List<MemberRow> members, List<TaskRow> tasks, List<DisputeRow> disputes,
        List<TransactionRow> transactions, List<AuditRow> auditLogs
) {
    public record MemberRow(Long id, String email, String nickname, String role, String status,
                            int availablePum, int reservedPum, String createdAtLabel) {}
    public record TaskRow(Long id, String title, String requesterName, String workerName,
                          String status, int requestedPum, String deadlineLabel, boolean cancellable) {}
    public record DisputeRow(Long id, Long taskId, String taskTitle, String openedBy, String openedByRole,
                             String description, String status, String createdAtLabel) {}
    public record TransactionRow(Long id, String memberName, String type, int deltaPum,
                                 int balancePum, String reason, String createdAtLabel) {}
    public record AuditRow(Long id, String adminName, String action, String target,
                           String details, String createdAtLabel) {}
}

