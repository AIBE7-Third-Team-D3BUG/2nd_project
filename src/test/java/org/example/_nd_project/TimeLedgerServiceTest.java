package org.example._nd_project;

import org.example._nd_project.member.TimeAccount;
import org.example._nd_project.member.TimeAccountRepository;
import org.example._nd_project.member.TimeLedgerService;
import org.example._nd_project.member.TimeTransaction;
import org.example._nd_project.member.TimeTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeLedgerServiceTest {

    @Mock TimeAccountRepository timeAccountRepository;
    @Mock TimeTransactionRepository timeTransactionRepository;

    private TimeLedgerService timeLedgerService;

    @BeforeEach
    void setUp() {
        timeLedgerService = new TimeLedgerService(timeAccountRepository, timeTransactionRepository);
    }

    @Test
    void increasingTaskPumOnlyReservesTheDifference() {
        TimeAccount account = new TimeAccount(3L, 300);
        account.reserve(120);
        when(timeTransactionRepository.sumReservedMinutesByTaskAndMember(10L, 3L)).thenReturn(120L);
        when(timeAccountRepository.findByMemberIdForUpdate(3L)).thenReturn(Optional.of(account));

        timeLedgerService.adjustTaskReservation(3L, 10L, 180);

        assertEquals(120, account.getAvailableMinutes());
        assertEquals(180, account.getReservedMinutes());
        verify(timeTransactionRepository).save(any(TimeTransaction.class));
    }

    @Test
    void decreasingTaskPumReturnsTheDifference() {
        TimeAccount account = new TimeAccount(3L, 180);
        account.reserve(120);
        when(timeTransactionRepository.sumReservedMinutesByTaskAndMember(10L, 3L)).thenReturn(120L);
        when(timeAccountRepository.findByMemberIdForUpdate(3L)).thenReturn(Optional.of(account));

        timeLedgerService.adjustTaskReservation(3L, 10L, 60);

        assertEquals(120, account.getAvailableMinutes());
        assertEquals(60, account.getReservedMinutes());
        verify(timeTransactionRepository).save(any(TimeTransaction.class));
    }

    @Test
    void deletingLegacyTaskWithoutReservationDoesNotCreateMoney() {
        TimeAccount account = new TimeAccount(3L, 120);
        when(timeAccountRepository.findByMemberIdForUpdate(3L)).thenReturn(Optional.of(account));
        when(timeTransactionRepository.sumReservedMinutesByTaskAndMember(10L, 3L)).thenReturn(0L);

        timeLedgerService.refundTaskReservation(3L, 10L);

        verify(timeTransactionRepository, never()).save(any(TimeTransaction.class));
        assertEquals(120, account.getAvailableMinutes());
        assertEquals(0, account.getReservedMinutes());
    }
}
