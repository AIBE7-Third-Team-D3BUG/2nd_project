package org.example._nd_project.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "time_accounts")
public class TimeAccount {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "available_minutes", nullable = false)
    private int availableMinutes;

    @Column(name = "reserved_minutes", nullable = false)
    private int reservedMinutes;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected TimeAccount() {
    }

    public TimeAccount(Long memberId, int initialMinutes) {
        this.memberId = memberId;
        this.availableMinutes = initialMinutes;
    }

    public void reserve(int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("예약 시간은 0보다 커야 합니다.");
        }
        if (availableMinutes < minutes) {
            throw new InsufficientBalanceException(availableMinutes, minutes);
        }
        availableMinutes -= minutes;
        reservedMinutes += minutes;
    }

    public void release(int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("반환 시간은 0보다 커야 합니다.");
        }
        if (reservedMinutes < minutes) {
            throw new IllegalStateException("예약 재화보다 많은 재화를 반환할 수 없습니다.");
        }
        reservedMinutes -= minutes;
        availableMinutes += minutes;
    }

    public Long getMemberId() { return memberId; }
    public int getAvailableMinutes() { return availableMinutes; }
    public int getReservedMinutes() { return reservedMinutes; }
}
