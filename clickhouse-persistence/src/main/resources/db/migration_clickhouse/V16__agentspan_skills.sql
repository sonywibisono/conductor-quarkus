-- AgentSpan skill storage (conductor.integrations.ai.enabled). Metadata + package bytes.
CREATE TABLE IF NOT EXISTS skill_metadata (
    name        String,
    version     String,
    is_latest   Bool DEFAULT false,
    detail_json String,
    created_at  Nullable(Int64),
    updated_at  Nullable(Int64)
) ENGINE = ReplacingMergeTree()
ORDER BY (name, version);

-- Package bytes are stored Base64-encoded
CREATE TABLE IF NOT EXISTS skill_package (
    handle     String,
    data       String,
    size_bytes Nullable(Int64),
    created_at Nullable(Int64)
) ENGINE = ReplacingMergeTree()
ORDER BY handle;
