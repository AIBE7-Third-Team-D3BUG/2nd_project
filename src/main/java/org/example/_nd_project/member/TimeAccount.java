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

    public Long getMemberId() { return memberId; }
    public int getAvailableMinutes() { return availableMinutes; }
    public int getReservedMinutes() { return reservedMinutes; }
}
