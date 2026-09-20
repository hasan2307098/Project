-- =====================================================================
-- NetPulse Enterprise - Relational schema (PostgreSQL 13+)
-- ---------------------------------------------------------------------
-- Run with:  psql -U netpulse -d netpulse -f database/schema.sql
-- A MySQL 8 variant lives in database/schema_mysql.sql
-- =====================================================================

BEGIN;

DROP TABLE IF EXISTS latency_logs CASCADE;
DROP TABLE IF EXISTS endpoints    CASCADE;

-- ---------------------------------------------------------------------
-- Table 1: monitored targets
-- ---------------------------------------------------------------------
CREATE TABLE endpoints (
    id                 BIGSERIAL     PRIMARY KEY,
    name               VARCHAR(120)  NOT NULL,
    target_address     VARCHAR(255)  NOT NULL,
    check_interval_sec INTEGER       NOT NULL DEFAULT 5,
    timeout_ms         INTEGER       NOT NULL DEFAULT 1500,
    is_active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_endpoints_name       UNIQUE (name),
    CONSTRAINT ck_endpoints_interval   CHECK (check_interval_sec BETWEEN 1 AND 3600),
    CONSTRAINT ck_endpoints_timeout    CHECK (timeout_ms         BETWEEN 100 AND 30000),
    CONSTRAINT ck_endpoints_name_len   CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_endpoints_target_len CHECK (LENGTH(TRIM(target_address)) > 0)
);

COMMENT ON TABLE  endpoints                IS 'Network targets monitored by NetPulse.';
COMMENT ON COLUMN endpoints.target_address IS 'Full URL (http/https -> HTTP HEAD probe) or host[:port] (-> raw TCP connect probe).';

-- ---------------------------------------------------------------------
-- Table 2: time-series latency samples
-- ---------------------------------------------------------------------
CREATE TABLE latency_logs (
    id             BIGSERIAL    PRIMARY KEY,
    endpoint_id    BIGINT       NOT NULL,
    latency_ms     INTEGER      NULL,            -- NULL when the target was unreachable
    is_reachable   BOOLEAN      NOT NULL,
    status_message VARCHAR(255) NULL,
    recorded_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_latency_logs_endpoint
        FOREIGN KEY (endpoint_id) REFERENCES endpoints (id) ON DELETE CASCADE,
    CONSTRAINT ck_latency_non_negative CHECK (latency_ms IS NULL OR latency_ms >= 0)
);

-- ---------------------------------------------------------------------
-- Indexes
--   The dashboard's hot query is
--     SELECT ... FROM latency_logs WHERE endpoint_id = ? ORDER BY recorded_at DESC LIMIT ?
--   The composite index with a DESC trailing column serves it as a short
--   index range scan, so history lookups stay fast as the table grows.
-- ---------------------------------------------------------------------
CREATE INDEX idx_latency_logs_endpoint_recorded
    ON latency_logs (endpoint_id, recorded_at DESC);

CREATE INDEX idx_latency_logs_recorded
    ON latency_logs (recorded_at DESC);          -- supports retention / purge jobs

CREATE INDEX idx_endpoints_active
    ON endpoints (is_active)
    WHERE is_active = TRUE;                      -- partial index: only active rows get scheduled

-- ---------------------------------------------------------------------
-- Seed data
-- ---------------------------------------------------------------------
INSERT INTO endpoints (name, target_address, check_interval_sec, timeout_ms, is_active) VALUES
    ('Google Public DNS', '8.8.8.8:53',                       5, 1500, TRUE),
    ('Cloudflare Portal', 'https://www.cloudflare.com',       5, 1500, TRUE),
    ('GitHub API',        'https://api.github.com',           8, 2000, TRUE),
    ('Local Backend',     'http://localhost:7070/api/health', 5, 1000, TRUE),
    ('Unreachable Demo',  '203.0.113.7:8080',                10, 1200, FALSE);

-- 20 synthetic samples per active endpoint so the history endpoint and the
-- chart have something to draw before the first live probe lands.
INSERT INTO latency_logs (endpoint_id, latency_ms, is_reachable, status_message, recorded_at)
SELECT e.id,
       (30 + (g * 7) % 90)::INT,
       TRUE,
       'seed sample',
       NOW() - (g || ' seconds')::INTERVAL
FROM endpoints e
CROSS JOIN generate_series(1, 20) AS g
WHERE e.is_active = TRUE;

COMMIT;

-- ---------------------------------------------------------------------
-- Optional retention helper (cron / pg_cron)
-- ---------------------------------------------------------------------
-- DELETE FROM latency_logs WHERE recorded_at < NOW() - INTERVAL '7 days';
