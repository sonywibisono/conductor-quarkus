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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.clickhouse.config.ClickHouseProperties;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.MetadataDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

/** ClickHouse implementation of MetadataDAO and EventHandlerDAO. */
public class ClickHouseMetadataDAO implements MetadataDAO, EventHandlerDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClickHouseMetadataDAO.class);

    private final ClickHouseProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, TaskDef> taskDefCache = new ConcurrentHashMap<>();

    public ClickHouseMetadataDAO(ClickHouseProperties properties, ObjectMapper objectMapper) {
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
                    "CREATE TABLE IF NOT EXISTS meta_task_def ("
                            + "name String, "
                            + "json_data String, "
                            + "version_epoch Int64"
                            + ") ENGINE = ReplacingMergeTree(version_epoch) "
                            + "ORDER BY name");

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS meta_workflow_def ("
                            + "name String, "
                            + "version Int32, "
                            + "json_data String, "
                            + "version_epoch Int64"
                            + ") ENGINE = ReplacingMergeTree(version_epoch) "
                            + "ORDER BY (name, version)");
        } catch (Exception e) {
            LOGGER.warn("ClickHouse metadata tables initialization: {}", e.getMessage());
        }
    }

    @Override
    public TaskDef createTaskDef(TaskDef taskDef) {
        return updateTaskDef(taskDef);
    }

    @Override
    public TaskDef updateTaskDef(TaskDef taskDef) {
        taskDef.setUpdateTime(System.currentTimeMillis());
        String sql = "INSERT INTO meta_task_def (name, json_data, version_epoch) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskDef.getName());
            ps.setString(2, objectMapper.writeValueAsString(taskDef));
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            taskDefCache.put(taskDef.getName(), taskDef);
        } catch (Exception e) {
            LOGGER.error("Failed to save TaskDef {}", taskDef.getName(), e);
        }
        return taskDef;
    }

    @Override
    public TaskDef getTaskDef(String name) {
        TaskDef cached = taskDefCache.get(name);
        if (cached != null) {
            return cached;
        }
        String sql = "SELECT json_data FROM meta_task_def FINAL WHERE name = ? LIMIT 1";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    TaskDef td = objectMapper.readValue(rs.getString(1), TaskDef.class);
                    taskDefCache.put(name, td);
                    return td;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get TaskDef {}", name, e);
        }
        return null;
    }

    @Override
    public List<TaskDef> getAllTaskDefs() {
        String sql = "SELECT json_data FROM meta_task_def FINAL";
        List<TaskDef> list = new ArrayList<>();
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TaskDef td = objectMapper.readValue(rs.getString(1), TaskDef.class);
                list.add(td);
                taskDefCache.put(td.getName(), td);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get all TaskDefs", e);
        }
        return list;
    }

    @Override
    public void removeTaskDef(String name) {
        String sql = "ALTER TABLE meta_task_def DELETE WHERE name = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
            taskDefCache.remove(name);
        } catch (Exception e) {
            LOGGER.error("Failed to remove TaskDef {}", name, e);
        }
    }

    @Override
    public void createWorkflowDef(WorkflowDef def) {
        updateWorkflowDef(def);
    }

    @Override
    public void updateWorkflowDef(WorkflowDef def) {
        def.setUpdateTime(System.currentTimeMillis());
        String sql =
                "INSERT INTO meta_workflow_def (name, version, json_data, version_epoch) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, def.getName());
            ps.setInt(2, def.getVersion());
            ps.setString(3, objectMapper.writeValueAsString(def));
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to save WorkflowDef {}:{}", def.getName(), def.getVersion(), e);
        }
    }

    @Override
    public Optional<WorkflowDef> getLatestWorkflowDef(String name) {
        List<WorkflowDef> defs = getAllVersions(name);
        return defs.stream().max(Comparator.comparingInt(WorkflowDef::getVersion));
    }

    @Override
    public Optional<WorkflowDef> getWorkflowDef(String name, int version) {
        String sql =
                "SELECT json_data FROM meta_workflow_def FINAL WHERE name = ? AND version = ? LIMIT 1";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(objectMapper.readValue(rs.getString(1), WorkflowDef.class));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get WorkflowDef {}:{}", name, version, e);
        }
        return Optional.empty();
    }

    @Override
    public void removeWorkflowDef(String name, Integer version) {
        String sql = "ALTER TABLE meta_workflow_def DELETE WHERE name = ? AND version = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, version);
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to remove WorkflowDef {}:{}", name, version, e);
        }
    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefs() {
        String sql = "SELECT json_data FROM meta_workflow_def FINAL";
        List<WorkflowDef> list = new ArrayList<>();
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(objectMapper.readValue(rs.getString(1), WorkflowDef.class));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get all WorkflowDefs", e);
        }
        return list;
    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefsLatestVersions() {
        return getAllWorkflowDefs().stream()
                .collect(
                        Collectors.groupingBy(
                                WorkflowDef::getName,
                                Collectors.maxBy(Comparator.comparingInt(WorkflowDef::getVersion))))
                .values()
                .stream()
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    private List<WorkflowDef> getAllVersions(String name) {
        String sql = "SELECT json_data FROM meta_workflow_def FINAL WHERE name = ?";
        List<WorkflowDef> list = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(objectMapper.readValue(rs.getString(1), WorkflowDef.class));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get versions for {}", name, e);
        }
        return list;
    }

    private final Map<String, EventHandler> eventHandlerMap = new ConcurrentHashMap<>();

    @Override
    public void addEventHandler(EventHandler eventHandler) {
        eventHandlerMap.put(eventHandler.getName(), eventHandler);
    }

    @Override
    public void updateEventHandler(EventHandler eventHandler) {
        eventHandlerMap.put(eventHandler.getName(), eventHandler);
    }

    @Override
    public void removeEventHandler(String name) {
        eventHandlerMap.remove(name);
    }

    @Override
    public List<EventHandler> getAllEventHandlers() {
        return new ArrayList<>(eventHandlerMap.values());
    }

    @Override
    public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
        return eventHandlerMap.values().stream()
                .filter(eh -> event.equals(eh.getEvent()) && (!activeOnly || eh.isActive()))
                .toList();
    }
}
