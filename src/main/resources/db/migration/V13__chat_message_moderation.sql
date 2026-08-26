ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS moderated_by_admin_id BIGINT REFERENCES members(id) ON DELETE RESTRICT;

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS moderation_reason VARCHAR(500);

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_chat_messages_moderated_at
    ON chat_messages (moderated_at DESC)
    WHERE moderated_at IS NOT NULL;
