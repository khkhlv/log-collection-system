-- Базовый датасет для всех чартов
SELECT
    id,
    created_at,
    received_at,
    level,
    source,
    host,
    environment,
    message,
    payload,
    format_type,
    DATE_TRUNC('hour', created_at) AS hour_bucket,
    DATE_TRUNC('day', created_at) AS day_bucket,
    CASE WHEN level IN ('ERROR', 'CRITICAL') THEN 1 ELSE 0 END AS is_error,
    payload->>'error_type' AS error_type,
    payload->>'user_id' AS user_id,
    payload->>'duration_ms' AS duration_ms,
    payload->>'http_status_code' AS http_status_code
FROM logs
WHERE created_at >= NOW() - INTERVAL '30 days'