-- No-op for ClickHouse:
-- B-Tree secondary indexes are not applicable in ClickHouse.
-- Vectorized columnar scans and sparse primary indexes handle correlation_id filtering.
SELECT 1;
