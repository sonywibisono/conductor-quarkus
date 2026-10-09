CREATE TABLE IF NOT EXISTS file_metadata (
    file_id                 String,
    file_name               String,
    content_type            String,
    storage_content_hash    Nullable(String),
    storage_content_size    Nullable(Int64),
    storage_type            String,
    storage_path            String,
    upload_status           String DEFAULT 'UPLOADING',
    workflow_id             String,
    task_id                 Nullable(String),
    created_at              DateTime DEFAULT now(),
    updated_at              DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY file_id;
