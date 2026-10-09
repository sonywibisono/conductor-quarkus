-- No-op for ClickHouse:
-- All tables were already created with their natural primary sorting keys in V1 via ReplacingMergeTree engines.
-- In ClickHouse, primary keys and ORDER BY cannot be modified via ALTER TABLE DROP/ADD CONSTRAINT.
SELECT 1;
