CREATE TABLE IF NOT EXISTS logs
(
    id           BIGSERIAL PRIMARY KEY,
    created_at   TIMESTAMPTZ      NOT NULL,
    received_at  TIMESTAMPTZ      NOT NULL,
    level        VARCHAR(20)      NOT NULL,
    source       VARCHAR(100)     NOT NULL,
    host         VARCHAR(255),
    environment  VARCHAR(50),
    message      TEXT,
    payload      JSONB,
    raw_content  TEXT,
    format_type  VARCHAR(20)
);

CREATE INDEX IF NOT EXISTS idx_logs_created_at
    ON logs (created_at);

CREATE INDEX IF NOT EXISTS idx_logs_level_source
    ON logs (level, source);

CREATE INDEX IF NOT EXISTS idx_logs_message
    ON logs USING GIN (to_tsvector('simple', COALESCE(message, '')));

