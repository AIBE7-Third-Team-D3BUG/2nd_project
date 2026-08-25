package org.example._nd_project.submission;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    boolean existsByTaskId(Long taskId);
}
