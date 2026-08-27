package org.example._nd_project.member;

import java.time.Instant;

public record TimeTransactionHistoryView(
        String label,
        String reason,
        int changePum,
        boolean credit,
        int availablePum,
        int reservedPum,
        Instant createdAt
) {
}
