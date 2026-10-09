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
import java.util.stream.Collectors;

import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.couchdb.client.CouchDbClient;
import com.netflix.conductor.couchdb.config.CouchDbProperties;
import com.netflix.conductor.dao.ConcurrentExecutionLimitDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.RateLimitingDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CouchDB implementation of ExecutionDAO. Stores Workflow execution instances, Task execution
 * instances, and events as JSON documents in CouchDB.
 */
public class CouchDbExecutionDAO
        implements ExecutionDAO, RateLimitingDAO, ConcurrentExecutionLimitDAO {

    private final CouchDbClient client;
    private final ObjectMapper objectMapper;
    private final String dbName;

    public CouchDbExecutionDAO(
            CouchDbClient client, ObjectMapper objectMapper, CouchDbProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.dbName = properties.getDbPrefix() + "execution";
        this.client.ensureDatabaseExists(this.dbName);
    }

    private String getWorkflowDocId(String workflowId) {
        return "workflow_" + workflowId;
    }

    private String getTaskDocId(String taskId) {
        return "task_" + taskId;
    }

    private String getEventDocId(String eventExecutionId) {
        return "event_" + eventExecutionId;
    }

    @Override
    public List<TaskModel> getPendingTasksByWorkflow(String taskName, String workflowId) {
        return getTasksForWorkflow(workflowId).stream()
                .filter(task -> task.getTaskDefName().equals(taskName))
                .filter(task -> task.getStatus() == TaskModel.Status.IN_PROGRESS)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskModel> getTasks(String taskType, String startKey, int count) {
        Map<String, Object> query =
                Map.of(
                        "selector",
                        Map.of("_id", Map.of("$regex", "^task_"), "taskType", taskType),
                        "limit",
                        count > 0 ? count : 100);
        JsonNode result = client.find(dbName, query);
        return parseDocs(result, TaskModel.class);
    }

    @Override
    public List<TaskModel> createTasks(List<TaskModel> tasks) {
        List<TaskModel> created = new ArrayList<>();
        for (TaskModel task : tasks) {
            client.putDocument(dbName, getTaskDocId(task.getTaskId()), task);
            created.add(task);
        }
        return created;
    }

    @Override
    public void updateTask(TaskModel task) {
        client.putDocument(dbName, getTaskDocId(task.getTaskId()), task);
    }

    @Override
    public boolean removeTask(String taskId) {
        return client.deleteDocument(dbName, getTaskDocId(taskId));
    }

    @Override
    public TaskModel getTask(String taskId) {
        return client.getDocument(dbName, getTaskDocId(taskId), TaskModel.class).orElse(null);
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
        Map<String, Object> query =
                Map.of(
                        "selector",
                        Map.of(
                                "_id",
                                Map.of("$regex", "^task_"),
                                "taskType",
                                taskType,
                                "status",
                                "IN_PROGRESS"),
                        "limit",
                        1000);
        JsonNode result = client.find(dbName, query);
        return parseDocs(result, TaskModel.class);
    }

    @Override
    public List<TaskModel> getTasksForWorkflow(String workflowId) {
        Map<String, Object> query =
                Map.of(
                        "selector",
                        Map.of("_id", Map.of("$regex", "^task_"), "workflowInstanceId", workflowId),
                        "limit",
                        1000);
        JsonNode result = client.find(dbName, query);
        return parseDocs(result, TaskModel.class);
    }

    @Override
    public String createWorkflow(WorkflowModel workflow) {
        workflow.setCreateTime(System.currentTimeMillis());
        workflow.setUpdatedTime(System.currentTimeMillis());
        client.putDocument(dbName, getWorkflowDocId(workflow.getWorkflowId()), workflow);
        return workflow.getWorkflowId();
    }

    @Override
    public String updateWorkflow(WorkflowModel workflow) {
        workflow.setUpdatedTime(System.currentTimeMillis());
        client.putDocument(dbName, getWorkflowDocId(workflow.getWorkflowId()), workflow);
        return workflow.getWorkflowId();
    }

    @Override
    public boolean removeWorkflow(String workflowId) {
        for (TaskModel task : getTasksForWorkflow(workflowId)) {
            removeTask(task.getTaskId());
        }
        return client.deleteDocument(dbName, getWorkflowDocId(workflowId));
    }

    @Override
    public boolean removeWorkflowWithExpiry(String workflowId, int ttlSeconds) {
        return removeWorkflow(workflowId);
    }

    @Override
    public void removeFromPendingWorkflow(String workflowType, String workflowId) {
        // No-op or update status if tracking pending workflows explicitly
    }

    @Override
    public WorkflowModel getWorkflow(String workflowId) {
        return getWorkflow(workflowId, true);
    }

    @Override
    public WorkflowModel getWorkflow(String workflowId, boolean includeTasks) {
        Optional<WorkflowModel> workflowOpt =
                client.getDocument(dbName, getWorkflowDocId(workflowId), WorkflowModel.class);
        if (workflowOpt.isEmpty()) {
            return null;
        }
        WorkflowModel workflow = workflowOpt.get();
        if (includeTasks) {
            workflow.setTasks(getTasksForWorkflow(workflowId));
        }
        return workflow;
    }

    @Override
    public List<String> getRunningWorkflowIds(String workflowName, int version) {
        return getPendingWorkflowsByType(workflowName, version).stream()
                .map(WorkflowModel::getWorkflowId)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowModel> getPendingWorkflowsByType(String workflowName, int version) {
        Map<String, Object> query =
                Map.of(
                        "selector",
                        Map.of(
                                "_id",
                                Map.of("$regex", "^workflow_"),
                                "workflowName",
                                workflowName,
                                "version",
                                version,
                                "status",
                                "RUNNING"),
                        "limit",
                        1000);
        JsonNode result = client.find(dbName, query);
        return parseDocs(result, WorkflowModel.class);
    }

    @Override
    public long getPendingWorkflowCount(String workflowName) {
        Map<String, Object> query =
                Map.of(
                        "selector",
                        Map.of(
                                "_id",
                                Map.of("$regex", "^workflow_"),
                                "workflowName",
                                workflowName,
                                "status",
                                "RUNNING"),
                        "limit",
                        10000);
        JsonNode result = client.find(dbName, query);
        return result.has("docs") ? result.get("docs").size() : 0L;
    }

    @Override
    public long getInProgressTaskCount(String taskDefName) {
        Map<String, Object> query =
                Map.of(
                        "selector",
                        Map.of(
                                "_id",
                                Map.of("$regex", "^task_"),
                                "taskDefName",
                                taskDefName,
                                "status",
                                "IN_PROGRESS"),
                        "limit",
                        10000);
        JsonNode result = client.find(dbName, query);
        return result.has("docs") ? result.get("docs").size() : 0L;
    }

    @Override
    public List<WorkflowModel> getWorkflowsByType(
            String workflowName, Long startTime, Long endTime) {
        Map<String, Object> selector = new HashMap<>();
        selector.put("_id", Map.of("$regex", "^workflow_"));
        selector.put("workflowName", workflowName);
        if (startTime != null && endTime != null) {
            selector.put("startTime", Map.of("$gte", startTime, "$lte", endTime));
        }
        Map<String, Object> query = Map.of("selector", selector, "limit", 1000);
        JsonNode result = client.find(dbName, query);
        return parseDocs(result, WorkflowModel.class);
    }

    @Override
    public List<WorkflowModel> getWorkflowsByCorrelationId(
            String workflowName, String correlationId, boolean includeTasks) {
        Map<String, Object> query =
                Map.of(
                        "selector",
                        Map.of(
                                "_id", Map.of("$regex", "^workflow_"),
                                "workflowName", workflowName,
                                "correlationId", correlationId),
                        "limit",
                        1000);
        JsonNode result = client.find(dbName, query);
        List<WorkflowModel> workflows = parseDocs(result, WorkflowModel.class);
        if (includeTasks) {
            for (WorkflowModel wf : workflows) {
                wf.setTasks(getTasksForWorkflow(wf.getWorkflowId()));
            }
        }
        return workflows;
    }

    @Override
    public boolean canSearchAcrossWorkflows() {
        return false;
    }

    @Override
    public boolean addEventExecution(EventExecution eventExecution) {
        String id = getEventDocId(eventExecution.getId());
        if (client.getDocument(dbName, id, EventExecution.class).isPresent()) {
            return false;
        }
        client.putDocument(dbName, id, eventExecution);
        return true;
    }

    @Override
    public void updateEventExecution(EventExecution eventExecution) {
        client.putDocument(dbName, getEventDocId(eventExecution.getId()), eventExecution);
    }

    @Override
    public void removeEventExecution(EventExecution eventExecution) {
        client.deleteDocument(dbName, getEventDocId(eventExecution.getId()));
    }

    private <T> List<T> parseDocs(JsonNode result, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        if (result != null && result.has("docs")) {
            for (JsonNode doc : result.get("docs")) {
                try {
                    list.add(objectMapper.treeToValue(doc, clazz));
                } catch (Exception ignored) {
                }
            }
        }
        return list;
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
}
