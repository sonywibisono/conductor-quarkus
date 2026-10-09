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
package com.netflix.conductor.couchdb.dao;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.couchdb.client.CouchDbClient;
import com.netflix.conductor.couchdb.config.CouchDbProperties;
import com.netflix.conductor.dao.MetadataDAO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CouchDB implementation of MetadataDAO. Stores workflow definitions and task definitions as
 * documents in CouchDB.
 */
public class CouchDbMetadataDAO implements MetadataDAO {

    private final CouchDbClient client;
    private final ObjectMapper objectMapper;
    private final String dbName;

    // Cache names and versions for fast retrieval
    private final Map<String, TaskDef> taskDefCache = new ConcurrentHashMap<>();

    public CouchDbMetadataDAO(
            CouchDbClient client, ObjectMapper objectMapper, CouchDbProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.dbName = properties.getDbPrefix() + "metadata";
        this.client.ensureDatabaseExists(this.dbName);
    }

    private String getTaskDefId(String name) {
        return "taskdef_" + name;
    }

    private String getWorkflowDefId(String name, int version) {
        return "workflowdef_" + name + "_" + version;
    }

    @Override
    public TaskDef createTaskDef(TaskDef taskDef) {
        taskDef.setCreateTime(System.currentTimeMillis());
        taskDef.setUpdateTime(System.currentTimeMillis());
        client.putDocument(dbName, getTaskDefId(taskDef.getName()), taskDef);
        taskDefCache.put(taskDef.getName(), taskDef);
        return taskDef;
    }

    @Override
    public TaskDef updateTaskDef(TaskDef taskDef) {
        taskDef.setUpdateTime(System.currentTimeMillis());
        client.putDocument(dbName, getTaskDefId(taskDef.getName()), taskDef);
        taskDefCache.put(taskDef.getName(), taskDef);
        return taskDef;
    }

    @Override
    public TaskDef getTaskDef(String name) {
        TaskDef cached = taskDefCache.get(name);
        if (cached != null) {
            return cached;
        }
        Optional<TaskDef> doc = client.getDocument(dbName, getTaskDefId(name), TaskDef.class);
        doc.ifPresent(td -> taskDefCache.put(name, td));
        return doc.orElse(null);
    }

    @Override
    public List<TaskDef> getAllTaskDefs() {
        Map<String, Object> query =
                Map.of("selector", Map.of("_id", Map.of("$regex", "^taskdef_")), "limit", 10000);
        JsonNode result = client.find(dbName, query);
        List<TaskDef> list = new ArrayList<>();
        if (result.has("docs")) {
            for (JsonNode doc : result.get("docs")) {
                try {
                    TaskDef td = objectMapper.treeToValue(doc, TaskDef.class);
                    list.add(td);
                    taskDefCache.put(td.getName(), td);
                } catch (Exception ignored) {
                }
            }
        }
        return list;
    }

    @Override
    public void removeTaskDef(String name) {
        client.deleteDocument(dbName, getTaskDefId(name));
        taskDefCache.remove(name);
    }

    @Override
    public void createWorkflowDef(WorkflowDef def) {
        def.setCreateTime(System.currentTimeMillis());
        def.setUpdateTime(System.currentTimeMillis());
        client.putDocument(dbName, getWorkflowDefId(def.getName(), def.getVersion()), def);
    }

    @Override
    public void updateWorkflowDef(WorkflowDef def) {
        def.setUpdateTime(System.currentTimeMillis());
        client.putDocument(dbName, getWorkflowDefId(def.getName(), def.getVersion()), def);
    }

    @Override
    public Optional<WorkflowDef> getLatestWorkflowDef(String name) {
        List<WorkflowDef> allVersions = getAllVersions(name);
        return allVersions.stream().max(Comparator.comparingInt(WorkflowDef::getVersion));
    }

    @Override
    public Optional<WorkflowDef> getWorkflowDef(String name, int version) {
        return client.getDocument(dbName, getWorkflowDefId(name, version), WorkflowDef.class);
    }

    @Override
    public void removeWorkflowDef(String name, Integer version) {
        client.deleteDocument(dbName, getWorkflowDefId(name, version));
    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefs() {
        Map<String, Object> query =
                Map.of(
                        "selector",
                        Map.of("_id", Map.of("$regex", "^workflowdef_")),
                        "limit",
                        10000);
        JsonNode result = client.find(dbName, query);
        List<WorkflowDef> list = new ArrayList<>();
        if (result.has("docs")) {
            for (JsonNode doc : result.get("docs")) {
                try {
                    WorkflowDef wd = objectMapper.treeToValue(doc, WorkflowDef.class);
                    list.add(wd);
                } catch (Exception ignored) {
                }
            }
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
        Map<String, Object> query =
                Map.of(
                        "selector",
                        Map.of("_id", Map.of("$regex", "^workflowdef_" + name + "_")),
                        "limit",
                        1000);
        JsonNode result = client.find(dbName, query);
        List<WorkflowDef> list = new ArrayList<>();
        if (result.has("docs")) {
            for (JsonNode doc : result.get("docs")) {
                try {
                    WorkflowDef wd = objectMapper.treeToValue(doc, WorkflowDef.class);
                    list.add(wd);
                } catch (Exception ignored) {
                }
            }
        }
        return list;
    }
}
