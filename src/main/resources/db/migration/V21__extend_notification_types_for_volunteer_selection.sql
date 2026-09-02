ALTER TABLE member_notifications
    DROP CONSTRAINT ck_member_notifications_type,
    ADD CONSTRAINT ck_member_notifications_type CHECK (
        type IN ('DELAY_RECORDED', 'DELAY_WARNING', 'APPLICATION_RESTRICTED',
                 'PENALTY_EXEMPTED', 'PENALTY_RESTORED',
                 'VOLUNTEER_NOT_SELECTED', 'VOLUNTEER_REOPENED')
    );
