-- Populate end_time for rows indexed before V18 in ClickHouse
ALTER TABLE workflow_index
UPDATE end_time = JSONExtractString(json_data, 'endTime')
WHERE JSONHas(json_data, 'endTime');

ALTER TABLE task_index
UPDATE end_time = JSONExtractString(json_data, 'endTime')
WHERE JSONHas(json_data, 'endTime');
