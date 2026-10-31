CREATE TABLE "${applicationSchema}".auth_tokens (
    token_hash CHAR(64) PRIMARY KEY CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    user_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id) ON DELETE CASCADE,
    credential_fingerprint CHAR(64) NOT NULL CHECK (credential_fingerprint ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at),
    revoked_at TIMESTAMPTZ CHECK (revoked_at >= created_at)
);
CREATE INDEX auth_tokens_user_id_idx ON "${applicationSchema}".auth_tokens(user_id);
CREATE INDEX auth_tokens_expires_at_idx ON "${applicationSchema}".auth_tokens(expires_at);
