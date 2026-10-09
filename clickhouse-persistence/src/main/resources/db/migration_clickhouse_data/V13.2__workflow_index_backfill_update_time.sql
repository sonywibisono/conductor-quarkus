-- Optional back-fill script to populate updateTime historically in ClickHouse
ALTER TABLE workflow_index
UPDATE update_time = JSONExtractString(json_data, 'updateTime')
WHERE JSONHas(json_data, 'updateTime');
