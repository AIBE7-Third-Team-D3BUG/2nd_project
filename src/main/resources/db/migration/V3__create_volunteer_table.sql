-- volunteer (업무 지원자) 테이블
CREATE TABLE volunteer (
    id          BIGSERIAL PRIMARY KEY,
    task_id     BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    member_id   BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    message     VARCHAR(500),
    status      VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_volunteer_task_member UNIQUE (task_id, member_id),
    CONSTRAINT ck_volunteer_status CHECK (status IN ('APPLIED', 'ACCEPTED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX idx_volunteer_task_id ON volunteer(task_id);
CREATE INDEX idx_volunteer_member_id ON volunteer(member_id);
