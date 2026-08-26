ALTER TABLE chat_rooms
    ADD COLUMN IF NOT EXISTS unread_message_count INTEGER NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_chat_rooms_unread_count'
          AND conrelid = 'chat_rooms'::regclass
    ) THEN
        ALTER TABLE chat_rooms
            ADD CONSTRAINT ck_chat_rooms_unread_count CHECK (unread_message_count >= 0);
    END IF;
END
$$;

UPDATE chat_rooms
SET unread_message_count = COALESCE(unread_message_count, 0);
