-- Schema registry storage
CREATE TABLE IF NOT EXISTS meta_schema_def (
    name        String,
    version     Int32,
    json_data   String,
    created_on  DateTime DEFAULT now(),
    modified_on DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(modified_on)
ORDER BY (name, version);
