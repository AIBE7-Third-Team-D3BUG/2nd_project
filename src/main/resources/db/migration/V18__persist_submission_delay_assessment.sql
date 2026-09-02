ALTER TABLE submissions
    ADD COLUMN deadline_status VARCHAR(20),
    ADD COLUMN late_minutes INTEGER,
    ADD COLUMN severe_threshold_minutes INTEGER,
    ADD COLUMN deadline_assessed_at TIMESTAMPTZ;

UPDATE submissions submission
SET deadline_status = CASE
        WHEN submission.created_at <= task.deadline_at THEN 'ON_TIME'
        WHEN submission.created_at <= task.deadline_at + INTERVAL '10 minutes' THEN 'GRACE'
        WHEN submission.created_at < task.deadline_at
                + make_interval(mins => GREATEST(30, LEAST(120, task.requested_minutes / 2)))
            THEN 'LATE'
        ELSE 'SEVERE'
    END,
    late_minutes = CASE
        WHEN submission.created_at <= task.deadline_at THEN 0
        ELSE CEIL(EXTRACT(EPOCH FROM (submission.created_at - task.deadline_at)) / 60.0)::INTEGER
    END,
    severe_threshold_minutes = GREATEST(30, LEAST(120, task.requested_minutes / 2)),
    deadline_assessed_at = submission.created_at
FROM tasks task
WHERE task.id = submission.task_id;

ALTER TABLE submissions
    ALTER COLUMN deadline_status SET NOT NULL,
    ALTER COLUMN late_minutes SET NOT NULL,
    ALTER COLUMN severe_threshold_minutes SET NOT NULL,
    ALTER COLUMN deadline_assessed_at SET NOT NULL,
    ADD CONSTRAINT ck_submissions_deadline_status CHECK (
        deadline_status IN ('ON_TIME', 'GRACE', 'LATE', 'SEVERE')
    ),
    ADD CONSTRAINT ck_submissions_late_minutes CHECK (late_minutes >= 0),
    ADD CONSTRAINT ck_submissions_severe_threshold CHECK (
        severe_threshold_minutes BETWEEN 30 AND 120
    );
