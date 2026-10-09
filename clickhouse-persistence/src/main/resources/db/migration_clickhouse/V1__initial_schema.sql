-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR METADATA DAO
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS meta_event_handler (
    name String,
    event String,
    active Bool DEFAULT false,
    json_data String,
    created_on DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY (name, event);

CREATE TABLE IF NOT EXISTS meta_task_def (
    name String,
    json_data String,
    created_on DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY name;

CREATE TABLE IF NOT EXISTS meta_workflow_def (
    name String,
    version Int32,
    latest_version Int32 DEFAULT 0,
    json_data String,
    created_on DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY (name, version);

-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR EXECUTION DAO
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS event_execution (
    event_handler_name String,
    event_name String,
    execution_id String,
    message_id String,
    json_data String,
    created_on DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY (event_handler_name, event_name, execution_id);

CREATE TABLE IF NOT EXISTS poll_data (
    queue_name String,
    domain String,
    json_data String,
    created_on DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY (queue_name, domain);

CREATE TABLE IF NOT EXISTS task_scheduled (
    workflow_id String,
    task_key String,
    task_id String,
    created_on DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY (workflow_id, task_key);

CREATE TABLE IF NOT EXISTS task_in_progress (
    task_def_name String,
    task_id String,
    workflow_id String,
    in_progress_status Bool DEFAULT false,
    created_on DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY (task_def_name, task_id);

CREATE TABLE IF NOT EXISTS task (
    task_id String,
    json_data String,
    created_on DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY task_id;

CREATE TABLE IF NOT EXISTS workflow (
    workflow_id String,
    correlation_id Nullable(String),
    json_data String,
    created_on DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY workflow_id;

CREATE TABLE IF NOT EXISTS workflow_def_to_workflow (
    workflow_def String,
    date_str String DEFAULT '',
    workflow_id String,
    created_on DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY (workflow_def, date_str, workflow_id);

CREATE TABLE IF NOT EXISTS workflow_pending (
    workflow_type String,
    workflow_id String,
    created_on DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY (workflow_type, workflow_id);

CREATE TABLE IF NOT EXISTS workflow_to_task (
    workflow_id String,
    task_id String,
    created_on DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY (workflow_id, task_id);

-- --------------------------------------------------------------------------------------------------------------
-- SCHEMA FOR QUEUE DAO
-- --------------------------------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS queue (
    queue_name String,
    created_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree()
ORDER BY queue_name;

CREATE TABLE IF NOT EXISTS queue_message (
    queue_name String,
    message_id String,
    deliver_on DateTime DEFAULT now(),
    priority Int32 DEFAULT 0,
    popped Bool DEFAULT false,
    offset_time_seconds Nullable(Int64),
    payload Nullable(String),
    created_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree()
ORDER BY (queue_name, message_id);
