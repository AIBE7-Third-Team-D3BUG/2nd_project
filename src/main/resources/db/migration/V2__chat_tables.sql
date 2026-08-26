-- 채팅방
CREATE TABLE IF NOT EXISTS chat_rooms (
    id                      BIGSERIAL PRIMARY KEY,
    task_id                 BIGINT NOT NULL UNIQUE REFERENCES tasks(id) ON DELETE RESTRICT,
    requester_member_id     BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    worker_member_id        BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    task_title              VARCHAR(120) NOT NULL,
    task_status             VARCHAR(20) NOT NULL,
    last_message_preview    VARCHAR(500),
    last_message_at         TIMESTAMPTZ,
    unread_message_count    INTEGER NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_chat_rooms_distinct_members CHECK (requester_member_id <> worker_member_id),
    CONSTRAINT ck_chat_rooms_title_not_blank CHECK (BTRIM(task_title) <> ''),
    CONSTRAINT ck_chat_rooms_task_status CHECK (BTRIM(task_status) <> ''),
    CONSTRAINT ck_chat_rooms_unread_count CHECK (unread_message_count >= 0)
);

-- 채팅 메시지
CREATE TABLE IF NOT EXISTS chat_messages (
    id              BIGSERIAL PRIMARY KEY,
    room_id         BIGINT NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_id       BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    content         VARCHAR(2000) NOT NULL,
    attachment_name VARCHAR(255),
    attachment_path VARCHAR(1500),
    attachment_size BIGINT,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMPTZ,
    CONSTRAINT ck_chat_messages_content_not_blank CHECK (BTRIM(content) <> '')
);

CREATE INDEX IF NOT EXISTS idx_chat_rooms_requester_updated ON chat_rooms (requester_member_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_rooms_worker_updated ON chat_rooms (worker_member_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_room_sent ON chat_messages (room_id, sent_at ASC, id ASC);
