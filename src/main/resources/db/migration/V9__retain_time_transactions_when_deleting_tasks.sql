-- 취소된 업무를 삭제해도 품 거래내역은 보존한다.
ALTER TABLE time_transactions
    DROP CONSTRAINT IF EXISTS time_transactions_task_id_fkey;

ALTER TABLE time_transactions
    ADD CONSTRAINT time_transactions_task_id_fkey
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE SET NULL;

-- 업무 삭제로 인한 task_id 해제 외에는 거래내역을 계속 수정할 수 없게 유지한다.
CREATE OR REPLACE FUNCTION prevent_time_transaction_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.task_id IS NOT NULL
       AND NEW.task_id IS NULL
       AND NEW.id = OLD.id
       AND NEW.account_member_id = OLD.account_member_id
       AND NEW.transaction_group_id = OLD.transaction_group_id
       AND NEW.transaction_type = OLD.transaction_type
       AND NEW.available_delta_minutes = OLD.available_delta_minutes
       AND NEW.reserved_delta_minutes = OLD.reserved_delta_minutes
       AND NEW.available_balance_after = OLD.available_balance_after
       AND NEW.reserved_balance_after = OLD.reserved_balance_after
       AND NEW.idempotency_key = OLD.idempotency_key
       AND NEW.related_transaction_id IS NOT DISTINCT FROM OLD.related_transaction_id
       AND NEW.reason = OLD.reason
       AND NEW.created_at = OLD.created_at THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'time_transactions is append-only; create a reversal transaction instead';
END;
$$ LANGUAGE plpgsql;
