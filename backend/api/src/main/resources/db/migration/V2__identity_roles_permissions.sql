CREATE TABLE "${applicationSchema}".users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL CHECK (btrim(name) <> ''),
    email VARCHAR(320) NOT NULL UNIQUE CHECK (email = lower(btrim(email)) AND email <> ''),
    password_hash VARCHAR(60) NOT NULL CHECK (password_hash ~ '^[$]2[aby][$][0-9]{2}[$][./A-Za-z0-9]{53}$'),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "${applicationSchema}".roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    name TEXT NOT NULL CHECK (btrim(name) <> ''),
    description TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "${applicationSchema}".permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource VARCHAR(128) NOT NULL CHECK (resource ~ '^[a-z][a-z0-9_.]*$'),
    action VARCHAR(64) NOT NULL CHECK (action ~ '^[a-z][a-z0-9_]*$'),
    description TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (resource, action)
);

CREATE TABLE "${applicationSchema}".user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES "${applicationSchema}".roles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, role_id)
);

CREATE TABLE "${applicationSchema}".role_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL REFERENCES "${applicationSchema}".roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES "${applicationSchema}".permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (role_id, permission_id)
);

CREATE INDEX user_roles_role_id_idx ON "${applicationSchema}".user_roles(role_id);
CREATE INDEX role_permissions_permission_id_idx ON "${applicationSchema}".role_permissions(permission_id);
