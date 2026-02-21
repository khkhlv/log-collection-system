SELECT
    source,
    MAX(created_at) AS last_log_time,
    EXTRACT(EPOCH FROM (NOW() - MAX(created_at))) / 3600 AS hours_since_last_log,
    CASE
        WHEN MAX(created_at) < NOW() - INTERVAL '1 hour'
        THEN 'DEAD'
        ELSE 'ACTIVE'
    END AS status
FROM logs
GROUP BY source
HAVING MAX(created_at) < NOW() - INTERVAL '1 hour' OR MAX(created_at) >= NOW() - INTERVAL '1 hour'
ORDER BY hours_since_last_log DESC