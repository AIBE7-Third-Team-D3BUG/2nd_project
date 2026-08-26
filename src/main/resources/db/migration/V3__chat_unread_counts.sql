ALTER TABLE chat_rooms
    ADD COLUMN IF NOT EXISTS unread_message_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE chat_rooms
    ADD CONSTRAINT IF NOT EXISTS ck_chat_rooms_unread_count CHECK (unread_message_count >= 0);

UPDATE chat_rooms
SET unread_message_count = COALESCE(unread_message_count, 0);
