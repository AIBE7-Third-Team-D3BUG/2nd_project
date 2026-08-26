CREATE TABLE admin_audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    admin_member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    action          VARCHAR(60) NOT NULL,
    target_type     VARCHAR(40) NOT NULL,
    target_id       BIGINT NOT NULL,
    details         VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_admin_audit_action CHECK (BTRIM(action) <> ''),
    CONSTRAINT ck_admin_audit_target CHECK (BTRIM(target_type) <> ''),
    CONSTRAINT ck_admin_audit_details CHECK (BTRIM(details) <> '')
);

CREATE INDEX ix_admin_audit_created ON admin_audit_logs (created_at DESC, id DESC);
CREATE INDEX ix_admin_audit_admin ON admin_audit_logs (admin_member_id, created_at DESC);
