-- Keep the conversation after a requester cancels an active task so the worker
-- can receive a clear cancellation notice instead of landing on a missing room.
ALTER TABLE chat_rooms
    ADD COLUMN IF NOT EXISTS task_deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE chat_rooms
    ALTER COLUMN task_id DROP NOT NULL;

ALTER TABLE chat_rooms
    DROP CONSTRAINT IF EXISTS chat_rooms_task_id_fkey;

ALTER TABLE chat_rooms
    ADD CONSTRAINT chat_rooms_task_id_fkey
        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE SET NULL;
