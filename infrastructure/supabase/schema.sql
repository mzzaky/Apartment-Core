-- =====================================================================
-- ApartmentCore Pro - License Management Schema (Supabase / PostgreSQL)
-- =====================================================================
-- Deploy this schema to your Supabase project via the SQL Editor.
-- The Cloudflare Worker reads/writes to these tables using the
-- Supabase REST API (PostgREST).
-- =====================================================================

-- 1. License keys table
CREATE TABLE IF NOT EXISTS licenses (
    id              UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    license_key     TEXT NOT NULL UNIQUE,
    owner_email     TEXT,
    owner_name      TEXT,
    status          TEXT NOT NULL DEFAULT 'active'
                        CHECK (status IN ('active', 'expired', 'revoked', 'suspended')),
    max_servers     INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,                       -- NULL = lifetime
    notes           TEXT
);

-- Index for fast lookup by key
CREATE INDEX IF NOT EXISTS idx_licenses_key ON licenses (license_key);

-- 2. Server activations (which servers have validated a license)
CREATE TABLE IF NOT EXISTS license_activations (
    id              UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    license_id      UUID NOT NULL REFERENCES licenses(id) ON DELETE CASCADE,
    server_id       TEXT NOT NULL,
    server_ip       TEXT NOT NULL,
    server_port     INT NOT NULL DEFAULT 25565,
    plugin_version  TEXT,
    first_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_active       BOOLEAN NOT NULL DEFAULT true,

    UNIQUE(license_id, server_id)
);

CREATE INDEX IF NOT EXISTS idx_activations_license ON license_activations (license_id);

-- 3. Validation log (audit trail)
CREATE TABLE IF NOT EXISTS validation_log (
    id              BIGSERIAL PRIMARY KEY,
    license_id      UUID REFERENCES licenses(id) ON DELETE SET NULL,
    license_key     TEXT NOT NULL,
    server_id       TEXT,
    server_ip       TEXT,
    result          TEXT NOT NULL CHECK (result IN ('valid', 'invalid', 'expired', 'revoked', 'over_limit', 'error')),
    message         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_validation_log_time ON validation_log (created_at DESC);

-- =====================================================================
-- Row Level Security (RLS)
-- =====================================================================
-- Enable RLS on all tables. The Cloudflare Worker uses the service_role
-- key so it bypasses RLS. Direct client access is blocked.
-- =====================================================================

ALTER TABLE licenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE license_activations ENABLE ROW LEVEL SECURITY;
ALTER TABLE validation_log ENABLE ROW LEVEL SECURITY;

-- Service role can do everything (used by Cloudflare Worker)
CREATE POLICY "service_role_all" ON licenses
    FOR ALL USING (auth.role() = 'service_role');
CREATE POLICY "service_role_all" ON license_activations
    FOR ALL USING (auth.role() = 'service_role');
CREATE POLICY "service_role_all" ON validation_log
    FOR ALL USING (auth.role() = 'service_role');

-- =====================================================================
-- Helper function: generate a formatted license key
-- Usage: SELECT generate_license_key();
-- Output: ACPRO-XXXX-XXXX-XXXX-XXXX
-- =====================================================================

CREATE OR REPLACE FUNCTION generate_license_key()
RETURNS TEXT AS $$
DECLARE
    chars TEXT := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    result TEXT := 'ACPRO-';
    i INT;
    g INT;
BEGIN
    FOR g IN 1..4 LOOP
        FOR i IN 1..4 LOOP
            result := result || substr(chars, floor(random() * length(chars) + 1)::int, 1);
        END LOOP;
        IF g < 4 THEN
            result := result || '-';
        END IF;
    END LOOP;
    RETURN result;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- Helper: insert a new license with auto-generated key
-- Usage: SELECT * FROM create_license('user@email.com', 'User Name', 1);
-- =====================================================================

CREATE OR REPLACE FUNCTION create_license(
    p_email TEXT,
    p_name TEXT,
    p_max_servers INT DEFAULT 1,
    p_expires_at TIMESTAMPTZ DEFAULT NULL
)
RETURNS TABLE(license_key TEXT, id UUID) AS $$
DECLARE
    new_key TEXT;
    new_id UUID;
BEGIN
    -- Generate unique key
    LOOP
        new_key := generate_license_key();
        EXIT WHEN NOT EXISTS (SELECT 1 FROM licenses l WHERE l.license_key = new_key);
    END LOOP;

    INSERT INTO licenses (license_key, owner_email, owner_name, max_servers, expires_at)
    VALUES (new_key, p_email, p_name, p_max_servers, p_expires_at)
    RETURNING licenses.id INTO new_id;

    RETURN QUERY SELECT new_key, new_id;
END;
$$ LANGUAGE plpgsql;
