CREATE TABLE oauth_accounts (
    id                  BIGSERIAL PRIMARY KEY,
    member_id           BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    provider            VARCHAR(30) NOT NULL,
    provider_user_id    VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_oauth_accounts_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_oauth_accounts_member_provider UNIQUE (member_id, provider),
    CONSTRAINT ck_oauth_accounts_provider_not_blank CHECK (BTRIM(provider) <> ''),
    CONSTRAINT ck_oauth_accounts_user_not_blank CHECK (BTRIM(provider_user_id) <> '')
);

CREATE INDEX idx_oauth_accounts_member_id ON oauth_accounts (member_id);
