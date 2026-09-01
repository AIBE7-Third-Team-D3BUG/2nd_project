ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS reference_link_url VARCHAR(1500);

ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS attachment_object_path VARCHAR(1500);

UPDATE tasks
SET reference_link_url = reference_file_url
WHERE reference_link_url IS NULL
  AND reference_file_url ~* '^https?://';

UPDATE tasks
SET attachment_object_path = reference_file_url
WHERE attachment_object_path IS NULL
  AND reference_file_url IS NOT NULL
  AND reference_file_url !~* '^https?://';
