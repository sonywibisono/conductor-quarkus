CREATE TABLE IF NOT EXISTS locks (
    lock_id String,
    lease_expiration DateTime64(3, 'UTC')
) ENGINE = ReplacingMergeTree()
ORDER BY lock_id;
