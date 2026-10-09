-- No-op for ClickHouse:
-- queue_message was already created with primary sort key ORDER BY (queue_name, message_id) in V1.
SELECT 1;
