SELECT
    ((to_timestamp(sampled_at / 1000.0) AT TIME ZONE 'UTC'))::date AS day_utc,
    world_id,
    MAX(online_count) AS peak_online,
    AVG(online_count) AS avg_online,
    COUNT(*)::bigint AS sample_count
FROM online_samples
WHERE sampled_at >= ?
  AND sampled_at < ?
  AND (?::integer IS NULL OR world_id = ?::integer)
GROUP BY 1, world_id
ORDER BY 1 ASC, world_id ASC
