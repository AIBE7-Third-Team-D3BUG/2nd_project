package org.example._nd_project.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "time_transactions")
public class TimeTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_member_id", nullable = false)
    private Long accountMemberId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "transaction_group_id", nullable = false, length = 64)
    private String transactionGroupId;

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType;

    @Column(name = "available_delta_minutes", nullable = false)
    private int availableDeltaMinutes;

    @Column(name = "reserved_delta_minutes", nullable = false)
    private int reservedDeltaMinutes;

    @Column(name = "available_balance_after", nullable = false)
    private int availableBalanceAfter;

    @Column(name = "reserved_balance_after", nullable = false)
    private int reservedBalanceAfter;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "related_transaction_id")
    private Long relatedTransactionId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected TimeTransaction() {
    }

    public static TimeTransaction signupReward(Long memberId, int minutes, String transactionId) {
        TimeTransaction transaction = new TimeTransaction();
        transaction.accountMemberId = memberId;
        transaction.transactionGroupId = transactionId;
        transaction.transactionType = "SIGNUP_REWARD";
        transaction.availableDeltaMinutes = minutes;
        transaction.availableBalanceAfter = minutes;
        transaction.idempotencyKey = "signup:" + memberId;
        transaction.reason = "신규 회원 체험 시간 지급";
        return transaction;
    }

    public static TimeTransaction taskReservationAdjustment(
            Long memberId,
            Long taskId,
            int reservationDifference,
            int availableBalanceAfter,
            int reservedBalanceAfter,
            String transactionId,
            String reason
    ) {
        TimeTransaction transaction = new TimeTransaction();
        transaction.accountMemberId = memberId;
        transaction.taskId = taskId;
        transaction.transactionGroupId = transactionId;
        transaction.transactionType = reservationDifference > 0 ? "TASK_RESERVE" : "TASK_REFUND";
        transaction.availableDeltaMinutes = -reservationDifference;
        transaction.reservedDeltaMinutes = reservationDifference;
        transaction.availableBalanceAfter = availableBalanceAfter;
        transaction.reservedBalanceAfter = reservedBalanceAfter;
        transaction.idempotencyKey = "task-balance:" + taskId + ":" + transactionId;
        transaction.reason = reason;
        return transaction;
    }

    public static TimeTransaction taskSettlementDebit(
            Long requesterId,
            Long taskId,
            int minutes,
            int availableBalanceAfter,
            int reservedBalanceAfter,
            String transactionGroupId
    ) {
        TimeTransaction transaction = new TimeTransaction();
        transaction.accountMemberId = requesterId;
        transaction.taskId = taskId;
        transaction.transactionGroupId = transactionGroupId;
        transaction.transactionType = "TASK_SETTLEMENT_DEBIT";
        transaction.reservedDeltaMinutes = -minutes;
        transaction.availableBalanceAfter = availableBalanceAfter;
        transaction.reservedBalanceAfter = reservedBalanceAfter;
        transaction.idempotencyKey = "task:" + taskId + ":settlement:requester";
        transaction.reason = "업무 완료 승인에 따른 예약 재화 정산";
        return transaction;
    }

    public static TimeTransaction taskSettlementCredit(
            Long workerId,
            Long taskId,
            int minutes,
            int availableBalanceAfter,
            int reservedBalanceAfter,
            String transactionGroupId
    ) {
        TimeTransaction transaction = new TimeTransaction();
        transaction.accountMemberId = workerId;
        transaction.taskId = taskId;
        transaction.transactionGroupId = transactionGroupId;
        transaction.transactionType = "TASK_SETTLEMENT_CREDIT";
        transaction.availableDeltaMinutes = minutes;
        transaction.availableBalanceAfter = availableBalanceAfter;
        transaction.reservedBalanceAfter = reservedBalanceAfter;
        transaction.idempotencyKey = "task:" + taskId + ":settlement:worker";
        transaction.reason = "업무 완료 승인에 따른 작업자 재화 지급";
        return transaction;
    }

    public static TimeTransaction adminAdjustment(
            Long memberId,
            int availableDeltaMinutes,
            int availableBalanceAfter,
            int reservedBalanceAfter,
            Long adminId,
            String transactionId,
            String reason
    ) {
        TimeTransaction transaction = new TimeTransaction();
        transaction.accountMemberId = memberId;
        transaction.transactionGroupId = transactionId;
        transaction.transactionType = availableDeltaMinutes > 0 ? "ADMIN_CREDIT" : "ADMIN_DEBIT";
        transaction.availableDeltaMinutes = availableDeltaMinutes;
        transaction.availableBalanceAfter = availableBalanceAfter;
        transaction.reservedBalanceAfter = reservedBalanceAfter;
        transaction.idempotencyKey = "admin:" + adminId + ":" + transactionId;
        transaction.reason = reason;
        return transaction;
    }

    public Long getId() { return id; }
    public Long getAccountMemberId() { return accountMemberId; }
    public Long getTaskId() { return taskId; }
    public String getTransactionType() { return transactionType; }
    public int getAvailableDeltaMinutes() { return availableDeltaMinutes; }
    public int getReservedDeltaMinutes() { return reservedDeltaMinutes; }
    public int getAvailableBalanceAfter() { return availableBalanceAfter; }
    public int getReservedBalanceAfter() { return reservedBalanceAfter; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
