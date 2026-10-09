-- End time columns for workflow_index and task_index
ALTER TABLE workflow_index
    ADD COLUMN IF NOT EXISTS end_time Nullable(String);

ALTER TABLE task_index
    ADD COLUMN IF NOT EXISTS end_time Nullable(String);
