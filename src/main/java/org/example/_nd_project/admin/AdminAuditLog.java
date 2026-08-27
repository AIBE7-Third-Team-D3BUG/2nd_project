package org.example._nd_project.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "admin_member_id", nullable = false)
    private Long adminMemberId;
    @Column(nullable = false, length = 60)
    private String action;
    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;
    @Column(name = "target_id", nullable = false)
    private Long targetId;
    @Column(nullable = false, length = 1000)
    private String details;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditLog() {}

    public static AdminAuditLog create(Long adminMemberId, String action, String targetType,
                                       Long targetId, String details) {
        AdminAuditLog log = new AdminAuditLog();
        log.adminMemberId = adminMemberId;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.details = details;
        return log;
    }

    public Long getId() { return id; }
    public Long getAdminMemberId() { return adminMemberId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}

