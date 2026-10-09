-- No-op for ClickHouse:
-- In ClickHouse, event_execution primary sort key was already configured as (event_handler_name, event_name, execution_id) in V1.
-- B-Tree secondary indexes are not applicable in ClickHouse.
SELECT 1;
