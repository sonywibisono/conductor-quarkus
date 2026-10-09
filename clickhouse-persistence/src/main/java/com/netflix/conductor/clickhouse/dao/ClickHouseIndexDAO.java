/*
 * Copyright 2023 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.clickhouse.dao;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.clickhouse.config.ClickHouseProperties;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.IndexDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ClickHouse implementation of IndexDAO. Stores workflow summaries, task summaries, execution logs
 * and events in ClickHouse using ReplacingMergeTree and MergeTree engines.
 */
public class ClickHouseIndexDAO implements IndexDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClickHouseIndexDAO.class);

    private final ClickHouseProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    public ClickHouseIndexDAO(ClickHouseProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executorService =
                new ThreadPoolExecutor(
                        properties.getAsyncWorkers(),
                        properties.getAsyncWorkers(),
                        1L,
                        TimeUnit.MINUTES,
                        new LinkedBlockingQueue<>(properties.getAsyncQueueCapacity()),
                        r -> {
                            Thread t = new Thread(r);
                            t.setName("clickhouse-index-async-" + t.getId());
                            t.setDaemon(true);
                            return t;
                        });

        if (properties.isAutoInit()) {
            try {
                setup();
            } catch (Exception e) {
                LOGGER.warn(
                        "Could not auto-initialize ClickHouse tables on startup: {}",
                        e.getMessage());
            }
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                properties.getUrl(), properties.getUsername(), properties.getPassword());
    }

    @Override
    public void setup() throws Exception {
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            // Workflow Index with ReplacingMergeTree for deduplication on update_time
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS workflow_index ("
                            + "workflow_id String, "
                            + "correlation_id Nullable(String), "
                            + "workflow_type String, "
                            + "status String, "
                            + "start_time Nullable(String), "
                            + "end_time Nullable(String), "
                            + "update_time Nullable(String), "
                            + "json_data String, "
                            + "version_epoch Int64"
                            + ") ENGINE = ReplacingMergeTree(version_epoch) "
                            + "ORDER BY (workflow_type, workflow_id)");

            // Task Index with ReplacingMergeTree
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS task_index ("
                            + "task_id String, "
                            + "task_type String, "
                            + "task_def_name String, "
                            + "status String, "
                            + "start_time Nullable(String), "
                            + "end_time Nullable(String), "
                            + "update_time Nullable(String), "
                            + "workflow_type String, "
                            + "json_data String, "
                            + "version_epoch Int64"
                            + ") ENGINE = ReplacingMergeTree(version_epoch) "
                            + "ORDER BY (task_type, task_id)");

            // Append-only logs with MergeTree
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS task_execution_logs ("
                            + "task_id String, "
                            + "log String, "
                            + "created_time Int64"
                            + ") ENGINE = MergeTree() "
                            + "ORDER BY (task_id, created_time)");

            // Event executions
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS event_executions ("
                            + "id String, "
                            + "event String, "
                            + "handler_name String, "
                            + "status String, "
                            + "created_time Int64, "
                            + "json_data String"
                            + ") ENGINE = MergeTree() "
                            + "ORDER BY (event, created_time)");

            // Message index
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS message_index ("
                            + "queue_name String, "
                            + "message_id String, "
                            + "payload String, "
                            + "created_time Int64"
                            + ") ENGINE = MergeTree() "
                            + "ORDER BY (queue_name, created_time)");

            LOGGER.info("ClickHouse index tables verified/created successfully.");
        }
    }

    @Override
    public void indexWorkflow(WorkflowSummary workflow) {
        String sql =
                "INSERT INTO workflow_index (workflow_id, correlation_id, workflow_type, status, start_time, end_time, update_time, json_data, version_epoch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflow.getWorkflowId());
            ps.setString(2, workflow.getCorrelationId());
            ps.setString(3, workflow.getWorkflowType());
            ps.setString(4, workflow.getStatus() != null ? workflow.getStatus().toString() : "");
            ps.setString(5, workflow.getStartTime());
            ps.setString(6, workflow.getEndTime());
            ps.setString(7, workflow.getUpdateTime());
            ps.setString(8, objectMapper.writeValueAsString(workflow));
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to index workflow {} in ClickHouse", workflow.getWorkflowId(), e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncIndexWorkflow(WorkflowSummary workflow) {
        return CompletableFuture.runAsync(() -> indexWorkflow(workflow), executorService);
    }

    @Override
    public void indexTask(TaskSummary task) {
        String sql =
                "INSERT INTO task_index (task_id, task_type, task_def_name, status, start_time, end_time, update_time, workflow_type, json_data, version_epoch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, task.getTaskId());
            ps.setString(2, task.getTaskType());
            ps.setString(3, task.getTaskDefName());
            ps.setString(4, task.getStatus() != null ? task.getStatus().toString() : "");
            ps.setString(5, task.getStartTime());
            ps.setString(6, task.getEndTime());
            ps.setString(7, task.getUpdateTime());
            ps.setString(8, task.getWorkflowType());
            ps.setString(9, objectMapper.writeValueAsString(task));
            ps.setLong(10, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to index task {} in ClickHouse", task.getTaskId(), e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncIndexTask(TaskSummary task) {
        return CompletableFuture.runAsync(() -> indexTask(task), executorService);
    }

    @Override
    public SearchResult<String> searchWorkflows(
            String query, String freeText, int start, int count, List<String> sort) {
        SearchResult<WorkflowSummary> full =
                searchWorkflowSummary(query, freeText, start, count, sort);
        List<String> ids = new ArrayList<>();
        for (WorkflowSummary s : full.getResults()) {
            ids.add(s.getWorkflowId());
        }
        return new SearchResult<>(full.getTotalHits(), ids);
    }

    @Override
    public SearchResult<WorkflowSummary> searchWorkflowSummary(
            String query, String freeText, int start, int count, List<String> sort) {
        String sql = "SELECT json_data FROM workflow_index FINAL LIMIT ? OFFSET ?";
        List<WorkflowSummary> list = new ArrayList<>();
        long total = 0;
        try (Connection conn = getConnection()) {
            try (Statement countStmt = conn.createStatement();
                    ResultSet rsCount =
                            countStmt.executeQuery("SELECT count() FROM workflow_index FINAL")) {
                if (rsCount.next()) {
                    total = rsCount.getLong(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, count > 0 ? count : 100);
                ps.setInt(2, Math.max(0, start));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String json = rs.getString(1);
                        list.add(objectMapper.readValue(json, WorkflowSummary.class));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to search workflows in ClickHouse", e);
        }
        return new SearchResult<>(total, list);
    }

    @Override
    public SearchResult<String> searchTasks(
            String query, String freeText, int start, int count, List<String> sort) {
        SearchResult<TaskSummary> full = searchTaskSummary(query, freeText, start, count, sort);
        List<String> ids = new ArrayList<>();
        for (TaskSummary s : full.getResults()) {
            ids.add(s.getTaskId());
        }
        return new SearchResult<>(full.getTotalHits(), ids);
    }

    @Override
    public SearchResult<TaskSummary> searchTaskSummary(
            String query, String freeText, int start, int count, List<String> sort) {
        String sql = "SELECT json_data FROM task_index FINAL LIMIT ? OFFSET ?";
        List<TaskSummary> list = new ArrayList<>();
        long total = 0;
        try (Connection conn = getConnection()) {
            try (Statement countStmt = conn.createStatement();
                    ResultSet rsCount =
                            countStmt.executeQuery("SELECT count() FROM task_index FINAL")) {
                if (rsCount.next()) {
                    total = rsCount.getLong(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, count > 0 ? count : 100);
                ps.setInt(2, Math.max(0, start));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String json = rs.getString(1);
                        list.add(objectMapper.readValue(json, TaskSummary.class));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to search tasks in ClickHouse", e);
        }
        return new SearchResult<>(total, list);
    }

    @Override
    public void removeWorkflow(String workflowId) {
        String sql = "ALTER TABLE workflow_index DELETE WHERE workflow_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to remove workflow index {}", workflowId, e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncRemoveWorkflow(String workflowId) {
        return CompletableFuture.runAsync(() -> removeWorkflow(workflowId), executorService);
    }

    @Override
    public void removeTask(String workflowId, String taskId) {
        String sql = "ALTER TABLE task_index DELETE WHERE task_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to remove task index {}", taskId, e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncRemoveTask(String workflowId, String taskId) {
        return CompletableFuture.runAsync(() -> removeTask(workflowId, taskId), executorService);
    }

    @Override
    public void updateWorkflow(String workflowInstanceId, String[] keys, Object[] values) {
        // Handled via indexWorkflow replacement
    }

    @Override
    public CompletableFuture<Void> asyncUpdateWorkflow(
            String workflowInstanceId, String[] keys, Object[] values) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String get(String workflowInstanceId, String key) {
        return null;
    }

    @Override
    public void updateTask(String workflowId, String taskId, String[] keys, Object[] values) {
        LOGGER.debug("updateTask handled via indexTask replacement in ClickHouse");
    }

    @Override
    public CompletableFuture<Void> asyncUpdateTask(
            String workflowId, String taskId, String[] keys, Object[] values) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void addTaskExecutionLogs(List<TaskExecLog> logs) {
        String sql =
                "INSERT INTO task_execution_logs (task_id, log, created_time) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (TaskExecLog log : logs) {
                ps.setString(1, log.getTaskId());
                ps.setString(2, log.getLog());
                ps.setLong(
                        3,
                        log.getCreatedTime() > 0
                                ? log.getCreatedTime()
                                : System.currentTimeMillis());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            LOGGER.error("Failed to add task execution logs to ClickHouse", e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncAddTaskExecutionLogs(List<TaskExecLog> logs) {
        return CompletableFuture.runAsync(() -> addTaskExecutionLogs(logs), executorService);
    }

    @Override
    public List<TaskExecLog> getTaskExecutionLogs(String taskId) {
        String sql =
                "SELECT task_id, log, created_time FROM task_execution_logs WHERE task_id = ? ORDER BY created_time ASC";
        List<TaskExecLog> list = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TaskExecLog l = new TaskExecLog();
                    l.setTaskId(rs.getString(1));
                    l.setLog(rs.getString(2));
                    l.setCreatedTime(rs.getLong(3));
                    list.add(l);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get task execution logs for {}", taskId, e);
        }
        return list;
    }

    @Override
    public void addMessage(String queue, Message msg) {
        String sql =
                "INSERT INTO message_index (queue_name, message_id, payload, created_time) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, queue);
            ps.setString(2, msg.getId());
            ps.setString(3, msg.getPayload());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to add message to ClickHouse index", e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncAddMessage(String queue, Message message) {
        return CompletableFuture.runAsync(() -> addMessage(queue, message), executorService);
    }

    @Override
    public List<Message> getMessages(String queue) {
        String sql =
                "SELECT message_id, payload FROM message_index WHERE queue_name = ? ORDER BY created_time DESC LIMIT 100";
        List<Message> list = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, queue);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Message(rs.getString(1), rs.getString(2), null));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get messages for queue {}", queue, e);
        }
        return list;
    }

    @Override
    public List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays) {
        return Collections.emptyList();
    }

    @Override
    public long getWorkflowCount(String query, String freeText) {
        String sql = "SELECT count() FROM workflow_index FINAL";
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get workflow count from ClickHouse", e);
        }
        return 0L;
    }

    @Override
    public void addEventExecution(EventExecution eventExecution) {
        String sql =
                "INSERT INTO event_executions (id, event, handler_name, status, created_time, json_data) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventExecution.getId());
            ps.setString(2, eventExecution.getEvent());
            ps.setString(3, eventExecution.getName());
            ps.setString(
                    4, eventExecution.getStatus() != null ? eventExecution.getStatus().name() : "");
            ps.setLong(5, eventExecution.getCreated());
            ps.setString(6, objectMapper.writeValueAsString(eventExecution));
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to add event execution to ClickHouse index", e);
        }
    }

    @Override
    public List<EventExecution> getEventExecutions(String event) {
        String sql =
                "SELECT json_data FROM event_executions WHERE event = ? ORDER BY created_time DESC LIMIT 100";
        List<EventExecution> list = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(objectMapper.readValue(rs.getString(1), EventExecution.class));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get event executions for {}", event, e);
        }
        return list;
    }

    @Override
    public CompletableFuture<Void> asyncAddEventExecution(EventExecution eventExecution) {
        return CompletableFuture.runAsync(() -> addEventExecution(eventExecution), executorService);
    }
}
