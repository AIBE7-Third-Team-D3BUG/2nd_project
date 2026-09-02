CREATE TABLE member_notifications (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    type        VARCHAR(40) NOT NULL,
    title       VARCHAR(120) NOT NULL,
    message     VARCHAR(500) NOT NULL,
    target_url  VARCHAR(500),
    event_key   VARCHAR(180) NOT NULL UNIQUE,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_member_notifications_type CHECK (
        type IN ('DELAY_RECORDED', 'DELAY_WARNING', 'APPLICATION_RESTRICTED',
                 'PENALTY_EXEMPTED', 'PENALTY_RESTORED')
    ),
    CONSTRAINT ck_member_notifications_title CHECK (BTRIM(title) <> ''),
    CONSTRAINT ck_member_notifications_message CHECK (BTRIM(message) <> ''),
    CONSTRAINT ck_member_notifications_target CHECK (
        target_url IS NULL OR (target_url LIKE '/%' AND target_url NOT LIKE '//%')
    ),
    CONSTRAINT ck_member_notifications_event_key CHECK (BTRIM(event_key) <> '')
);

CREATE INDEX idx_member_notifications_member_created
    ON member_notifications(member_id, created_at DESC, id DESC);

CREATE INDEX idx_member_notifications_unread
    ON member_notifications(member_id, created_at DESC)
    WHERE read_at IS NULL;
