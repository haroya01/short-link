-- DATETIME columns hold UTC wall-clock time, the way Hibernate writes an Instant. These five tables
-- were inserted with NOW() in the KST session, so those rows sit nine hours ahead. NOW() writes
-- whole seconds and the rows saved through JPA before it carry microseconds, which is how the
-- shifted rows are told apart (production 10-22: 68 whole-second rows, 3 fractional). Writers now
-- use UTC_TIMESTAMP(6), whose rows keep their microseconds.
UPDATE post_like SET created_at = created_at - INTERVAL 9 HOUR WHERE MICROSECOND(created_at) = 0;
UPDATE comment_like SET created_at = created_at - INTERVAL 9 HOUR WHERE MICROSECOND(created_at) = 0;
UPDATE highlight_reply_like SET created_at = created_at - INTERVAL 9 HOUR
WHERE MICROSECOND(created_at) = 0;
UPDATE post_bookmark SET created_at = created_at - INTERVAL 9 HOUR WHERE MICROSECOND(created_at) = 0;
UPDATE series_subscription SET created_at = created_at - INTERVAL 9 HOUR
WHERE MICROSECOND(created_at) = 0;
