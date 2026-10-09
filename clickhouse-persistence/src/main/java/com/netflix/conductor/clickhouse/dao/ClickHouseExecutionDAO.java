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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.clickhouse.config.ClickHouseProperties;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.dao.ConcurrentExecutionLimitDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.dao.RateLimitingDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ClickHouse implementation of ExecutionDAO. Uses ReplacingMergeTree for atomic state updates per
 * workflow and task.
 */
public class ClickHouseExecutionDAO
        implements ExecutionDAO, RateLimitingDAO, ConcurrentExecutionLimitDAO, PollDataDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClickHouseExecutionDAO.class);

    private final ClickHouseProperties properties;
    private final ObjectMapper objectMapper;

    public ClickHouseExecutionDAO(ClickHouseProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        initTables();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                properties.getUrl(), properties.getUsername(), properties.getPassword());
    }

    private void initTables() {
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS exec_workflow ("
                            + "workflow_id String, "
                            + "workflow_name String, "
                            + "version Int32, "
                            + "status String, "
                            + "correlation_id Nullable(String), "
                            + "start_time Int64, "
                            + "end_time Nullable(Int64), "
                            + "json_data String, "
                            + "version_epoch Int64"
                            + ") ENGINE = ReplacingMergeTree(version_epoch) "
                            + "ORDER BY (workflow_name, workflow_id)");

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS exec_task ("
                            + "task_id String, "
                            + "workflow_id String, "
                            + "task_type String, "
                            + "task_def_name String, "
                            + "status String, "
                            + "json_data String, "
                            + "version_epoch Int64"
                            + ") ENGINE = ReplacingMergeTree(version_epoch) "
                            + "ORDER BY (workflow_id, task_id)");

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS exec_event ("
                            + "id String, "
                            + "json_data String, "
                            + "version_epoch Int64"
                            + ") ENGINE = ReplacingMergeTree(version_epoch) "
                            + "ORDER BY id");
        } catch (Exception e) {
            LOGGER.warn("ClickHouse execution tables initialization: {}", e.getMessage());
        }
    }

    @Override
    public List<TaskModel> getPendingTasksByWorkflow(String taskName, String workflowId) {
        return getTasksForWorkflow(workflowId).stream()
                .filter(t -> t.getTaskDefName().equals(taskName))
                .filter(t -> t.getStatus() == TaskModel.Status.IN_PROGRESS)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskModel> getTasks(String taskType, String startKey, int count) {
        String sql = "SELECT json_data FROM exec_task FINAL WHERE task_type = ? LIMIT ?";
        List<TaskModel> list = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskType);
            ps.setInt(2, count > 0 ? count : 100);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(objectMapper.readValue(rs.getString(1), TaskModel.class));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get tasks of type {}", taskType, e);
        }
        return list;
    }

    @Override
    public List<TaskModel> createTasks(List<TaskModel> tasks) {
        String sql =
                "INSERT INTO exec_task (task_id, workflow_id, task_type, task_def_name, status, json_data, version_epoch) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (TaskModel t : tasks) {
                ps.setString(1, t.getTaskId());
                ps.setString(2, t.getWorkflowInstanceId());
                ps.setString(3, t.getTaskType());
                ps.setString(4, t.getTaskDefName());
                ps.setString(5, t.getStatus().name());
                ps.setString(6, objectMapper.writeValueAsString(t));
                ps.setLong(7, System.currentTimeMillis());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            LOGGER.error("Failed to create tasks", e);
        }
        return tasks;
    }

    @Override
    public void updateTask(TaskModel task) {
        String sql =
                "INSERT INTO exec_task (task_id, workflow_id, task_type, task_def_name, status, json_data, version_epoch) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, task.getTaskId());
            ps.setString(2, task.getWorkflowInstanceId());
            ps.setString(3, task.getTaskType());
            ps.setString(4, task.getTaskDefName());
            ps.setString(5, task.getStatus().name());
            ps.setString(6, objectMapper.writeValueAsString(task));
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to update task {}", task.getTaskId(), e);
        }
    }

    @Override
    public boolean removeTask(String taskId) {
        String sql = "ALTER TABLE exec_task DELETE WHERE task_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to remove task {}", taskId, e);
            return false;
        }
    }

    @Override
    public TaskModel getTask(String taskId) {
        String sql = "SELECT json_data FROM exec_task FINAL WHERE task_id = ? LIMIT 1";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return objectMapper.readValue(rs.getString(1), TaskModel.class);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get task {}", taskId, e);
        }
        return null;
    }

    @Override
    public List<TaskModel> getTasks(List<String> taskIds) {
        return taskIds.stream()
                .map(this::getTask)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskModel> getPendingTasksForTaskType(String taskType) {
        String sql =
                "SELECT json_data FROM exec_task FINAL WHERE task_type = ? AND status = 'IN_PROGRESS' LIMIT 1000";
        List<TaskModel> list = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(objectMapper.readValue(rs.getString(1), TaskModel.class));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get pending tasks for {}", taskType, e);
        }
        return list;
    }

    @Override
    public List<TaskModel> getTasksForWorkflow(String workflowId) {
        String sql = "SELECT json_data FROM exec_task FINAL WHERE workflow_id = ?";
        List<TaskModel> list = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(objectMapper.readValue(rs.getString(1), TaskModel.class));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get tasks for workflow {}", workflowId, e);
        }
        return list;
    }

    @Override
    public String createWorkflow(WorkflowModel workflow) {
        workflow.setCreateTime(System.currentTimeMillis());
        workflow.setUpdatedTime(System.currentTimeMillis());
        String sql =
                "INSERT INTO exec_workflow (workflow_id, workflow_name, version, status, correlation_id, start_time, end_time, json_data, version_epoch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflow.getWorkflowId());
            ps.setString(2, workflow.getWorkflowName());
            ps.setInt(3, workflow.getWorkflowVersion());
            ps.setString(4, workflow.getStatus().name());
            ps.setString(5, workflow.getCorrelationId());
            ps.setLong(6, workflow.getCreateTime() != null ? workflow.getCreateTime() : 0L);
            ps.setObject(7, workflow.getEndTime());
            ps.setString(8, objectMapper.writeValueAsString(workflow));
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to create workflow {}", workflow.getWorkflowId(), e);
        }
        return workflow.getWorkflowId();
    }

    @Override
    public String updateWorkflow(WorkflowModel workflow) {
        workflow.setUpdatedTime(System.currentTimeMillis());
        return createWorkflow(workflow);
    }

    @Override
    public boolean removeWorkflow(String workflowId) {
        String sql = "ALTER TABLE exec_workflow DELETE WHERE workflow_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.executeUpdate();
            for (TaskModel t : getTasksForWorkflow(workflowId)) {
                removeTask(t.getTaskId());
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to remove workflow {}", workflowId, e);
            return false;
        }
    }

    @Override
    public boolean removeWorkflowWithExpiry(String workflowId, int ttlSeconds) {
        return removeWorkflow(workflowId);
    }

    @Override
    public void removeFromPendingWorkflow(String workflowType, String workflowId) {}

    @Override
    public WorkflowModel getWorkflow(String workflowId) {
        return getWorkflow(workflowId, true);
    }

    @Override
    public WorkflowModel getWorkflow(String workflowId, boolean includeTasks) {
        String sql = "SELECT json_data FROM exec_workflow FINAL WHERE workflow_id = ? LIMIT 1";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    WorkflowModel model =
                            objectMapper.readValue(rs.getString(1), WorkflowModel.class);
                    if (includeTasks) {
                        model.setTasks(getTasksForWorkflow(workflowId));
                    }
                    return model;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get workflow {}", workflowId, e);
        }
        return null;
    }

    @Override
    public List<String> getRunningWorkflowIds(String workflowName, int version) {
        return getPendingWorkflowsByType(workflowName, version).stream()
                .map(WorkflowModel::getWorkflowId)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowModel> getPendingWorkflowsByType(String workflowName, int version) {
        String sql =
                "SELECT json_data FROM exec_workflow FINAL WHERE workflow_name = ? AND version = ? AND status = 'RUNNING' LIMIT 1000";
        List<WorkflowModel> list = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowName);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(objectMapper.readValue(rs.getString(1), WorkflowModel.class));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get pending workflows for {}", workflowName, e);
        }
        return list;
    }

    @Override
    public long getPendingWorkflowCount(String workflowName) {
        String sql =
                "SELECT count() FROM exec_workflow FINAL WHERE workflow_name = ? AND status = 'RUNNING'";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to count pending workflows for {}", workflowName, e);
        }
        return 0L;
    }

    @Override
    public long getInProgressTaskCount(String taskDefName) {
        String sql =
                "SELECT count() FROM exec_task FINAL WHERE task_def_name = ? AND status = 'IN_PROGRESS'";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskDefName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to count in progress tasks for {}", taskDefName, e);
        }
        return 0L;
    }

    @Override
    public List<WorkflowModel> getWorkflowsByType(
            String workflowName, Long startTime, Long endTime) {
        String sql = "SELECT json_data FROM exec_workflow FINAL WHERE workflow_name = ? LIMIT 1000";
        List<WorkflowModel> list = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(objectMapper.readValue(rs.getString(1), WorkflowModel.class));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get workflows by type {}", workflowName, e);
        }
        return list;
    }

    @Override
    public List<WorkflowModel> getWorkflowsByCorrelationId(
            String workflowName, String correlationId, boolean includeTasks) {
        String sql =
                "SELECT json_data FROM exec_workflow FINAL WHERE workflow_name = ? AND correlation_id = ? LIMIT 1000";
        List<WorkflowModel> list = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowName);
            ps.setString(2, correlationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    WorkflowModel model =
                            objectMapper.readValue(rs.getString(1), WorkflowModel.class);
                    if (includeTasks) {
                        model.setTasks(getTasksForWorkflow(model.getWorkflowId()));
                    }
                    list.add(model);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get workflows by correlation id {}", correlationId, e);
        }
        return list;
    }

    @Override
    public boolean canSearchAcrossWorkflows() {
        return false;
    }

    @Override
    public boolean addEventExecution(EventExecution eventExecution) {
        updateEventExecution(eventExecution);
        return true;
    }

    @Override
    public void updateEventExecution(EventExecution eventExecution) {
        String sql = "INSERT INTO exec_event (id, json_data, version_epoch) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventExecution.getId());
            ps.setString(2, objectMapper.writeValueAsString(eventExecution));
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to save event execution {}", eventExecution.getId(), e);
        }
    }

    @Override
    public void removeEventExecution(EventExecution eventExecution) {
        String sql = "ALTER TABLE exec_event DELETE WHERE id = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventExecution.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to remove event execution {}", eventExecution.getId(), e);
        }
    }

    // RateLimitingDAO implementation
    @Override
    public boolean exceedsRateLimitPerFrequency(TaskModel task, TaskDef taskDef) {
        return false;
    }

    // ConcurrentExecutionLimitDAO implementation
    @Override
    public boolean exceedsLimit(TaskModel task) {
        return false;
    }

    // PollDataDAO implementation
    @Override
    public void updateLastPollData(String taskDefName, String domain, String workerId) {}

    @Override
    public PollData getPollData(String taskDefName, String domain) {
        return null;
    }

    @Override
    public List<PollData> getPollData(String taskDefName) {
        return Collections.emptyList();
    }
}
