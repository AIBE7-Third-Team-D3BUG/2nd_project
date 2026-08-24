package org.example._nd_project;

import org.example._nd_project.member.InsufficientBalanceException;
import org.example._nd_project.member.TimeAccount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeAccountTest {

    @Test
    void reserveAndReleaseMoveBalanceWithoutChangingTotal() {
        TimeAccount account = new TimeAccount(1L, 240);

        account.reserve(120);
        assertEquals(120, account.getAvailableMinutes());
        assertEquals(120, account.getReservedMinutes());

        account.release(60);
        assertEquals(180, account.getAvailableMinutes());
        assertEquals(60, account.getReservedMinutes());
    }

    @Test
    void cannotReserveMoreThanAvailableBalance() {
        TimeAccount account = new TimeAccount(1L, 120);

        assertThrows(InsufficientBalanceException.class, () -> account.reserve(180));
        assertEquals(120, account.getAvailableMinutes());
        assertEquals(0, account.getReservedMinutes());
    }

    @Test
    void settlementSpendsRequesterReservationAndCreditsWorker() {
        TimeAccount requester = new TimeAccount(1L, 240);
        TimeAccount worker = new TimeAccount(2L, 60);
        requester.reserve(120);

        requester.spendReserved(120);
        worker.credit(120);

        assertEquals(120, requester.getAvailableMinutes());
        assertEquals(0, requester.getReservedMinutes());
        assertEquals(180, worker.getAvailableMinutes());
    }
}
