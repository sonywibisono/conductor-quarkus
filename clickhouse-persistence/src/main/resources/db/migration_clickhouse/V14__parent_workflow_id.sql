ALTER TABLE workflow_index
    ADD COLUMN IF NOT EXISTS parent_workflow_id String DEFAULT '';
