-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR INDEX DAO
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS workflow_index (
    workflow_id String,
    correlation_id Nullable(String),
    workflow_type String,
    status String,
    start_time Nullable(String),
    json_data String,
    version_epoch Int64 DEFAULT toInt64(toUnixTimestamp64Milli(now64(3)))
) ENGINE = ReplacingMergeTree(version_epoch)
ORDER BY (workflow_type, workflow_id);

CREATE TABLE IF NOT EXISTS task_index (
    task_id String,
    task_type String,
    task_def_name String,
    status String,
    start_time Nullable(String),
    update_time Nullable(String),
    workflow_type String,
    json_data String,
    version_epoch Int64 DEFAULT toInt64(toUnixTimestamp64Milli(now64(3)))
) ENGINE = ReplacingMergeTree(version_epoch)
ORDER BY (task_type, task_id);

CREATE TABLE IF NOT EXISTS task_execution_logs (
    task_id String,
    log String,
    created_time Int64
) ENGINE = MergeTree()
ORDER BY (task_id, created_time);
