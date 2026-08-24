ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS attachment_name VARCHAR(255);

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS attachment_path VARCHAR(1500);

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS attachment_size BIGINT;
