-- 한 업무를 여러 번 수정할 때마다 예약/반환 거래를 원장에 누적할 수 있게 한다.
ALTER TABLE time_transactions
    DROP CONSTRAINT IF EXISTS uq_time_transactions_task_account_type;

CREATE INDEX IF NOT EXISTS idx_time_transactions_task_account_created
    ON time_transactions (task_id, account_member_id, created_at DESC);
