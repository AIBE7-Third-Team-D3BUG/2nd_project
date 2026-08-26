package org.example._nd_project.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<AdminAuditLog> {
    List<AdminAuditLog> findTop100ByOrderByCreatedAtDescIdDesc();
    @org.springframework.data.jpa.repository.Query("select log from AdminAuditLog log where log.adminMemberId in :memberIds or (log.targetType = 'MEMBER' and log.targetId in :memberIds) or (log.targetType = 'TASK' and log.targetId in :taskIds) or (log.targetType = 'DISPUTE' and log.targetId in :disputeIds)")
    org.springframework.data.domain.Page<AdminAuditLog> findRelatedToMembers(
            @org.springframework.data.repository.query.Param("memberIds") java.util.Collection<Long> memberIds,
            @org.springframework.data.repository.query.Param("taskIds") java.util.Collection<Long> taskIds,
            @org.springframework.data.repository.query.Param("disputeIds") java.util.Collection<Long> disputeIds,
            org.springframework.data.domain.Pageable pageable);
}
