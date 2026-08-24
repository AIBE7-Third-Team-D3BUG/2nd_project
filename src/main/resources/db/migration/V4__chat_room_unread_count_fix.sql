ALTER TABLE chat_rooms
    ADD COLUMN IF NOT EXISTS unread_message_count INTEGER NOT NULL DEFAULT 0;

UPDATE chat_rooms
SET unread_message_count = COALESCE(unread_message_count, 0)
WHERE unread_message_count IS NULL;
