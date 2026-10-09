-- Classifier of the workflow definition an execution was started from
ALTER TABLE workflow_index
    ADD COLUMN IF NOT EXISTS classifier Nullable(String);
