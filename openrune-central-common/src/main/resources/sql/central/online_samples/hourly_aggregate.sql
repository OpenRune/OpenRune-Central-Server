SELECT
    (sampled_at / 3600000) * 3600000 AS bucket_utc_millis,
    world_id,
    MAX(online_count) AS peak_online,
    AVG(online_count) AS avg_online
FROM online_samples
WHERE sampled_at >= ?
  AND sampled_at < ?
  AND (?::integer IS NULL OR world_id = ?::integer)
GROUP BY 1, world_id
ORDER BY 1 ASC, world_id ASC
