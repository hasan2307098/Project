-- =====================================================================
-- NetPulse Enterprise - MySQL 8 variant
-- Run with:  mysql -u netpulse -p netpulse < database/schema_mysql.sql
-- =====================================================================

DROP TABLE IF EXISTS latency_logs;
DROP TABLE IF EXISTS endpoints;

CREATE TABLE endpoints (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(120) NOT NULL,
    target_address     VARCHAR(255) NOT NULL,
    check_interval_sec INT          NOT NULL DEFAULT 5,
    timeout_ms         INT          NOT NULL DEFAULT 1500,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_endpoints_name (name),
    CONSTRAINT ck_endpoints_interval CHECK (check_interval_sec BETWEEN 1 AND 3600),
    CONSTRAINT ck_endpoints_timeout  CHECK (timeout_ms BETWEEN 100 AND 30000)
) ENGINE=InnoDB;

CREATE TABLE latency_logs (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    endpoint_id    BIGINT       NOT NULL,
    latency_ms     INT          NULL,
    is_reachable   BOOLEAN      NOT NULL,
    status_message VARCHAR(255) NULL,
    recorded_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_latency_logs_endpoint FOREIGN KEY (endpoint_id)
        REFERENCES endpoints (id) ON DELETE CASCADE,
    INDEX idx_latency_logs_endpoint_recorded (endpoint_id, recorded_at DESC),
    INDEX idx_latency_logs_recorded (recorded_at DESC)
) ENGINE=InnoDB;

CREATE INDEX idx_endpoints_active ON endpoints (is_active);

INSERT INTO endpoints (name, target_address, check_interval_sec, timeout_ms, is_active) VALUES
    ('Google Public DNS', '8.8.8.8:53',                       5, 1500, TRUE),
    ('Cloudflare Portal', 'https://www.cloudflare.com',       5, 1500, TRUE),
    ('GitHub API',        'https://api.github.com',           8, 2000, TRUE),
    ('Local Backend',     'http://localhost:7070/api/health', 5, 1000, TRUE),
    ('Unreachable Demo',  '203.0.113.7:8080',                10, 1200, FALSE);
