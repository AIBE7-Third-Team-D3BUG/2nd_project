-- 이미 운영 DB에 존재하는 이전 채팅 스키마를 현재 애플리케이션 모델과 호환시킨다.
-- 기존 attachment_path는 롤백 호환성을 위해 유지하고 새 컬럼으로 데이터를 복사한다.
ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS attachment_object_path VARCHAR(1500);

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS attachment_content_type VARCHAR(150);

UPDATE chat_messages
SET attachment_object_path = attachment_path
WHERE attachment_object_path IS NULL
  AND attachment_path IS NOT NULL;

-- 이전 스키마는 텍스트가 없는 파일 메시지를 허용하지 않았다.
ALTER TABLE chat_messages
    DROP CONSTRAINT IF EXISTS ck_chat_messages_content_not_blank;

ALTER TABLE chat_messages
    DROP CONSTRAINT IF EXISTS ck_chat_messages_body;

ALTER TABLE chat_messages
    ADD CONSTRAINT ck_chat_messages_body CHECK (
        BTRIM(content) <> '' OR attachment_object_path IS NOT NULL
    );

-- 현재 ChatRoom 엔티티는 상태 컬럼을 직접 기록하지 않으므로 기존 필수 컬럼에 기본값을 둔다.
ALTER TABLE chat_rooms
    ALTER COLUMN task_status SET DEFAULT 'MATCHED';
