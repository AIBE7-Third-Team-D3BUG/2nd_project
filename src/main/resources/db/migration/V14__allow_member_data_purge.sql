-- Time transactions are append-only during normal operation. A member withdrawal
-- transaction can opt in to deleting only the withdrawing member's history.
CREATE OR REPLACE FUNCTION prevent_time_transaction_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE'
       AND current_setting('app.member_withdrawal', true) = 'on' THEN
        RETURN OLD;
    END IF;

    IF TG_OP = 'UPDATE'
       AND OLD.task_id IS NOT NULL
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
