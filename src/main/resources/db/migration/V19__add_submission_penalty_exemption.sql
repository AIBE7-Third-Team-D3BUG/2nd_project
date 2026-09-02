ALTER TABLE submissions
    ADD COLUMN penalty_exempted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN penalty_exemption_reason VARCHAR(450),
    ADD COLUMN penalty_exempted_by BIGINT,
    ADD COLUMN penalty_exempted_at TIMESTAMPTZ,
    ADD CONSTRAINT fk_submissions_penalty_exempted_by
        FOREIGN KEY (penalty_exempted_by) REFERENCES members(id),
    ADD CONSTRAINT ck_submissions_penalty_exemption_state CHECK (
        (penalty_exempted = FALSE
            AND penalty_exemption_reason IS NULL
            AND penalty_exempted_by IS NULL
            AND penalty_exempted_at IS NULL)
        OR
        (penalty_exempted = TRUE
            AND penalty_exemption_reason IS NOT NULL
            AND penalty_exempted_by IS NOT NULL
            AND penalty_exempted_at IS NOT NULL)
    );

CREATE INDEX idx_submissions_active_delay_metrics
    ON submissions(worker_id, deadline_assessed_at)
    WHERE penalty_exempted = FALSE;
