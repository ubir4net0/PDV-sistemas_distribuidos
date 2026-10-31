CREATE TABLE "${applicationSchema}".customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_type VARCHAR(2) NOT NULL CHECK (person_type IN ('PF', 'PJ')),
    document VARCHAR(14) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    phone VARCHAR(32),
    email VARCHAR(320),
    locality VARCHAR(160),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((person_type = 'PF' AND document ~ '^[0-9]{11}$')
        OR (person_type = 'PJ' AND document ~ '^[A-Z0-9]{12}[0-9]{2}$'))
);
