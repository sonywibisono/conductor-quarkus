ALTER TABLE workflow_index
    ADD COLUMN IF NOT EXISTS update_time Nullable(String);
