SELECT
    level,
    COUNT(*) AS log_count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) AS percentage
FROM logs
WHERE created_at >= NOW() - INTERVAL '24 hours'
GROUP BY level
ORDER BY log_count DESC