package org.example._nd_project.member;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeTransactionRepository extends JpaRepository<TimeTransaction, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);
}
