package org.example._nd_project.member;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TimeLedgerService {

    private final TimeAccountRepository timeAccountRepository;
    private final TimeTransactionRepository timeTransactionRepository;

    public TimeLedgerService(TimeAccountRepository timeAccountRepository,
                             TimeTransactionRepository timeTransactionRepository) {
        this.timeAccountRepository = timeAccountRepository;
        this.timeTransactionRepository = timeTransactionRepository;
    }

    @Transactional(readOnly = true)
    public int getAvailableMinutes(Long memberId) {
        return timeAccountRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("시간 계정을 찾을 수 없습니다."))
                .getAvailableMinutes();
    }

    @Transactional(readOnly = true)
    public int getTaskReservedMinutes(Long memberId, Long taskId) {
        return Math.toIntExact(
                timeTransactionRepository.sumReservedMinutesByTaskAndMember(taskId, memberId)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveForTask(Long memberId, Long taskId, int minutes) {
        setTaskReservation(memberId, taskId, minutes, "업무 등록 재화 예약");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void adjustTaskReservation(Long memberId, Long taskId, int nextMinutes) {
        setTaskReservation(memberId, taskId, nextMinutes, "업무 수정에 따른 예약 재화 조정");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refundTaskReservation(Long memberId, Long taskId) {
        refundTaskReservation(memberId, taskId, "업무 삭제에 따른 예약 재화 반환");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refundTaskReservation(Long memberId, Long taskId, String reason) {
        setTaskReservation(memberId, taskId, 0, reason);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void settleTask(Long requesterId, Long workerId, Long taskId, int minutes) {
        String requesterKey = "task:" + taskId + ":settlement:requester";
        String workerKey = "task:" + taskId + ":settlement:worker";
        if (timeTransactionRepository.existsByIdempotencyKey(requesterKey)
                || timeTransactionRepository.existsByIdempotencyKey(workerKey)) {
            throw new IllegalStateException("이미 정산된 업무입니다.");
        }

        int reservedMinutes = getTaskReservedMinutes(requesterId, taskId);
        if (reservedMinutes != minutes) {
            throw new IllegalStateException("업무 예약 재화와 정산 재화가 일치하지 않습니다.");
        }

        List<Long> memberIds = List.of(requesterId, workerId).stream().sorted().toList();
        Map<Long, TimeAccount> accounts = timeAccountRepository.findAllByMemberIdInForUpdate(memberIds)
                .stream()
                .collect(Collectors.toMap(TimeAccount::getMemberId, Function.identity()));
        TimeAccount requester = requireAccount(accounts, requesterId);
        TimeAccount worker = requireAccount(accounts, workerId);

        requester.spendReserved(minutes);
        worker.credit(minutes);

        String transactionGroupId = UUID.randomUUID().toString();
        timeTransactionRepository.saveAll(List.of(
                TimeTransaction.taskSettlementDebit(
                        requesterId,
                        taskId,
                        minutes,
                        requester.getAvailableMinutes(),
                        requester.getReservedMinutes(),
                        transactionGroupId
                ),
                TimeTransaction.taskSettlementCredit(
                        workerId,
                        taskId,
                        minutes,
                        worker.getAvailableMinutes(),
                        worker.getReservedMinutes(),
                        transactionGroupId
                )
        ));
    }

    private void setTaskReservation(Long memberId, Long taskId, int targetMinutes, String reason) {
        TimeAccount account = timeAccountRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("시간 계정을 찾을 수 없습니다."));
        int currentMinutes = getTaskReservedMinutes(memberId, taskId);
        int difference = targetMinutes - currentMinutes;
        if (difference != 0) {
            apply(account, memberId, taskId, difference, reason);
        }
    }

    private void apply(TimeAccount account, Long memberId, Long taskId,
                       int reservationDifference, String reason) {
        if (reservationDifference > 0) {
            account.reserve(reservationDifference);
        } else {
            account.release(-reservationDifference);
        }

        String transactionId = UUID.randomUUID().toString();
        timeTransactionRepository.save(TimeTransaction.taskReservationAdjustment(
                memberId,
                taskId,
                reservationDifference,
                account.getAvailableMinutes(),
                account.getReservedMinutes(),
                transactionId,
                reason
        ));
    }

    private TimeAccount requireAccount(Map<Long, TimeAccount> accounts, Long memberId) {
        TimeAccount account = accounts.get(memberId);
        if (account == null) {
            throw new IllegalStateException("시간 계정을 찾을 수 없습니다.");
        }
        return account;
    }
}
