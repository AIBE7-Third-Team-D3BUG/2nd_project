-- 긴급 재능 품앗이 플랫폼 MVP 스키마
-- Target: PostgreSQL 15+
-- 핵심 원칙: 1 Time Credit = 30분, 모든 거래 시간은 분 단위 정수로 저장

-- 1. 회원 + 프로필
CREATE TABLE members (
    id                      BIGSERIAL PRIMARY KEY,
    email                   VARCHAR(255) NOT NULL UNIQUE,
    password_hash           VARCHAR(255) NOT NULL,
    nickname                VARCHAR(30) NOT NULL UNIQUE,
    introduction            VARCHAR(1000),
    profile_image_url       VARCHAR(1000),
    portfolio_url           VARCHAR(1000),
    skill_tags              VARCHAR(50)[] NOT NULL DEFAULT '{}',
    notification_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    role                    VARCHAR(10) NOT NULL DEFAULT 'USER',
    status                  VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    completed_task_count    INTEGER NOT NULL DEFAULT 0,
    review_count            INTEGER NOT NULL DEFAULT 0,
    rating_sum              INTEGER NOT NULL DEFAULT 0,
    terms_agreed_at         TIMESTAMPTZ NOT NULL,
    privacy_agreed_at       TIMESTAMPTZ NOT NULL,
    last_login_at           TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_members_email_not_blank CHECK (BTRIM(email) <> ''),
    CONSTRAINT ck_members_nickname_not_blank CHECK (BTRIM(nickname) <> ''),
    CONSTRAINT ck_members_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_members_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT ck_members_counts CHECK (
        completed_task_count >= 0
        AND review_count >= 0
        AND rating_sum >= 0
        AND rating_sum <= review_count * 5
    )
);

-- 2. 긴급 업무 + AI 추천 후 선택된 수행자 정보
CREATE TABLE tasks (
    id                      BIGSERIAL PRIMARY KEY,
    requester_id            BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    worker_id               BIGINT REFERENCES members(id) ON DELETE RESTRICT,
    title                   VARCHAR(120) NOT NULL,
    description             TEXT NOT NULL,
    category                VARCHAR(30) NOT NULL,
    required_skill_tags     VARCHAR(50)[] NOT NULL DEFAULT '{}',
    requested_minutes       INTEGER NOT NULL,
    deadline_at             TIMESTAMPTZ NOT NULL,
    deliverable_description VARCHAR(500) NOT NULL,
    revision_limit          INTEGER NOT NULL DEFAULT 0,
    reference_file_url      VARCHAR(1500),
    caution                 VARCHAR(1000),
    status                  VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    matched_at              TIMESTAMPTZ,
    started_at              TIMESTAMPTZ,
    submitted_at            TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_tasks_title_not_blank CHECK (BTRIM(title) <> ''),
    CONSTRAINT ck_tasks_description_not_blank CHECK (BTRIM(description) <> ''),
    CONSTRAINT ck_tasks_deliverable_not_blank CHECK (BTRIM(deliverable_description) <> ''),
    CONSTRAINT ck_tasks_category CHECK (category IN (
        'PRESENTATION', 'DEVELOPMENT', 'DOCUMENT_REVIEW', 'TRANSLATION',
        'INTERVIEW', 'PORTFOLIO', 'DESIGN', 'DATA', 'ETC'
    )),
    CONSTRAINT ck_tasks_status CHECK (status IN (
        'OPEN', 'MATCHED', 'IN_PROGRESS', 'SUBMITTED',
        'COMPLETED', 'CANCELLED', 'DISPUTED'
    )),
    CONSTRAINT ck_tasks_requested_minutes CHECK (
        requested_minutes > 0 AND MOD(requested_minutes, 30) = 0
    ),
    CONSTRAINT ck_tasks_deadline CHECK (
        deadline_at > created_at
        AND deadline_at <= created_at + INTERVAL '24 hours'
    ),
    CONSTRAINT ck_tasks_revision_limit CHECK (revision_limit BETWEEN 0 AND 10),
    CONSTRAINT ck_tasks_distinct_members CHECK (
        worker_id IS NULL OR requester_id <> worker_id
    )
);

-- 3. 현재 시간 잔액
CREATE TABLE time_accounts (
    member_id           BIGINT PRIMARY KEY REFERENCES members(id) ON DELETE RESTRICT,
    available_minutes   INTEGER NOT NULL DEFAULT 0,
    reserved_minutes    INTEGER NOT NULL DEFAULT 0,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_time_accounts_available CHECK (
        available_minutes >= 0 AND MOD(available_minutes, 30) = 0
    ),
    CONSTRAINT ck_time_accounts_reserved CHECK (
        reserved_minutes >= 0 AND MOD(reserved_minutes, 30) = 0
    ),
    CONSTRAINT ck_time_accounts_version CHECK (version >= 0)
);

-- 4. 변경 불가능한 시간 거래 원장
CREATE TABLE time_transactions (
    id                          BIGSERIAL PRIMARY KEY,
    account_member_id           BIGINT NOT NULL REFERENCES time_accounts(member_id) ON DELETE RESTRICT,
    task_id                     BIGINT REFERENCES tasks(id) ON DELETE RESTRICT,
    transaction_group_id        VARCHAR(64) NOT NULL,
    transaction_type            VARCHAR(30) NOT NULL,
    available_delta_minutes     INTEGER NOT NULL DEFAULT 0,
    reserved_delta_minutes      INTEGER NOT NULL DEFAULT 0,
    available_balance_after     INTEGER NOT NULL,
    reserved_balance_after      INTEGER NOT NULL,
    idempotency_key             VARCHAR(100) NOT NULL UNIQUE,
    related_transaction_id      BIGINT REFERENCES time_transactions(id) ON DELETE RESTRICT,
    reason                      VARCHAR(500) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_time_transactions_task_account_type
        UNIQUE (task_id, account_member_id, transaction_type),
    CONSTRAINT ck_time_transactions_delta CHECK (
        (available_delta_minutes <> 0 OR reserved_delta_minutes <> 0)
        AND MOD(available_delta_minutes, 30) = 0
        AND MOD(reserved_delta_minutes, 30) = 0
    ),
    CONSTRAINT ck_time_transactions_balance CHECK (
        available_balance_after >= 0
        AND reserved_balance_after >= 0
        AND MOD(available_balance_after, 30) = 0
        AND MOD(reserved_balance_after, 30) = 0
    ),
    CONSTRAINT ck_time_transactions_reason CHECK (BTRIM(reason) <> ''),
    CONSTRAINT ck_time_transactions_type CHECK (transaction_type IN (
        'SIGNUP_REWARD',
        'TASK_RESERVE',
        'TASK_SETTLEMENT_DEBIT',
        'TASK_SETTLEMENT_CREDIT',
        'TASK_REFUND',
        'ADMIN_CREDIT',
        'ADMIN_DEBIT',
        'REVERSAL'
    )),
    CONSTRAINT ck_time_transactions_reversal CHECK (
        transaction_type <> 'REVERSAL' OR related_transaction_id IS NOT NULL
    )
);

-- 5. 결과물 제출
CREATE TABLE submissions (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT NOT NULL UNIQUE REFERENCES tasks(id) ON DELETE RESTRICT,
    worker_id           BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    result_description  TEXT NOT NULL,
    result_file_url     VARCHAR(1500),
    actual_minutes      INTEGER NOT NULL,
    requester_note      VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_submissions_result CHECK (BTRIM(result_description) <> ''),
    CONSTRAINT ck_submissions_actual_minutes CHECK (actual_minutes > 0)
);

-- 6. 채팅방
CREATE TABLE chat_rooms (
    id                      BIGSERIAL PRIMARY KEY,
    task_id                 BIGINT NOT NULL UNIQUE REFERENCES tasks(id) ON DELETE RESTRICT,
    requester_member_id     BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    worker_member_id        BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    task_title              VARCHAR(120) NOT NULL,
    task_status             VARCHAR(20) NOT NULL,
    last_message_preview    VARCHAR(500),
    last_message_at         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_chat_rooms_distinct_members CHECK (requester_member_id <> worker_member_id),
    CONSTRAINT ck_chat_rooms_title_not_blank CHECK (BTRIM(task_title) <> ''),
    CONSTRAINT ck_chat_rooms_task_status CHECK (BTRIM(task_status) <> '')
);

-- 7. 채팅 메시지
CREATE TABLE chat_messages (
    id              BIGSERIAL PRIMARY KEY,
    room_id         BIGINT NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_id       BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    content         VARCHAR(2000) NOT NULL,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMPTZ,
    CONSTRAINT ck_chat_messages_content_not_blank CHECK (BTRIM(content) <> '')
);

-- 6. 후기
CREATE TABLE reviews (
    id              BIGSERIAL PRIMARY KEY,
    task_id         BIGINT NOT NULL UNIQUE REFERENCES tasks(id) ON DELETE RESTRICT,
    reviewer_id     BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    reviewee_id     BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    rating          SMALLINT NOT NULL,
    content         VARCHAR(1000),
    deadline_met    BOOLEAN,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reviews_members CHECK (reviewer_id <> reviewee_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

-- 7. 분쟁
CREATE TABLE disputes (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT NOT NULL UNIQUE REFERENCES tasks(id) ON DELETE RESTRICT,
    opened_by_member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    dispute_type        VARCHAR(50) NOT NULL,
    description         TEXT NOT NULL,
    evidence_url        VARCHAR(1500),
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution_note     TEXT,
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_disputes_type CHECK (BTRIM(dispute_type) <> ''),
    CONSTRAINT ck_disputes_description CHECK (BTRIM(description) <> ''),
    CONSTRAINT ck_disputes_status CHECK (
        status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'REJECTED')
    ),
    CONSTRAINT ck_disputes_resolved_at CHECK (
        (status IN ('OPEN', 'UNDER_REVIEW') AND resolved_at IS NULL)
        OR (status IN ('RESOLVED', 'REJECTED') AND resolved_at IS NOT NULL)
    )
);

-- 조회 성능용 인덱스
CREATE INDEX idx_members_skill_tags ON members USING GIN (skill_tags);
CREATE INDEX idx_tasks_status_deadline ON tasks (status, deadline_at);
CREATE INDEX idx_tasks_category_status ON tasks (category, status);
CREATE INDEX idx_tasks_requester_created ON tasks (requester_id, created_at DESC);
CREATE INDEX idx_tasks_required_skill_tags ON tasks USING GIN (required_skill_tags);
CREATE INDEX idx_time_transactions_account_created
    ON time_transactions (account_member_id, created_at DESC, id DESC);
CREATE INDEX idx_time_transactions_group ON time_transactions (transaction_group_id);
CREATE INDEX idx_chat_rooms_requester_updated ON chat_rooms (requester_member_id, updated_at DESC);
CREATE INDEX idx_chat_rooms_worker_updated ON chat_rooms (worker_member_id, updated_at DESC);
CREATE INDEX idx_chat_messages_room_sent ON chat_messages (room_id, sent_at ASC, id ASC);
CREATE INDEX idx_disputes_status_created ON disputes (status, created_at);

-- updated_at 자동 갱신
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_members_updated_at BEFORE UPDATE ON members
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_tasks_updated_at BEFORE UPDATE ON tasks
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_time_accounts_updated_at BEFORE UPDATE ON time_accounts
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_submissions_updated_at BEFORE UPDATE ON submissions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_disputes_updated_at BEFORE UPDATE ON disputes
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 거래 원장은 UPDATE/DELETE 금지. 오류는 REVERSAL 거래를 추가해 보정한다.
CREATE OR REPLACE FUNCTION prevent_time_transaction_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'time_transactions is append-only; create a reversal transaction instead';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_time_transactions_no_update BEFORE UPDATE ON time_transactions
FOR EACH ROW EXECUTE FUNCTION prevent_time_transaction_mutation();

CREATE TRIGGER trg_time_transactions_no_delete BEFORE DELETE ON time_transactions
FOR EACH ROW EXECUTE FUNCTION prevent_time_transaction_mutation();

COMMENT ON TABLE time_accounts IS '빠른 잔액 조회를 위한 현재 상태 스냅샷';
COMMENT ON TABLE time_transactions IS '수정·삭제하지 않는 시간 거래 원장';
COMMENT ON COLUMN tasks.requested_minutes IS '요청자가 등록하고 매칭 시 예약되는 정산 기준 시간';
COMMENT ON COLUMN submissions.actual_minutes IS '통계·분쟁 참고 정보이며 정산 기준으로 사용하지 않음';
