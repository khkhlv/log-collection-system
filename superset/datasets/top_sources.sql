SELECT
    payload->>'error_type' AS error_type,
    COUNT(*) AS error_count
FROM logs
WHERE
    level = 'ERROR'
    AND payload->>'error_type' IS NOT NULL
    AND created_at >= NOW() - INTERVAL '24 hours'
GROUP BY payload->>'error_type'
ORDER BY error_count DESC
LIMIT 3