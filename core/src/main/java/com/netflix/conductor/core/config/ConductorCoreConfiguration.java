/*
 * Copyright 2021 Conductor Authors.
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
package com.netflix.conductor.core.config;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.conductoross.conductor.core.listener.MetadataChangeListener;
import org.conductoross.conductor.core.listener.MetadataChangeListenerStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.core.listener.TaskStatusListenerStub;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListenerStub;
import com.netflix.conductor.core.storage.DummyPayloadStorage;
import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.core.utils.IDGenerator;

import static com.netflix.conductor.core.events.EventQueues.EVENT_QUEUE_PROVIDERS_QUALIFIER;
import static com.netflix.conductor.core.execution.tasks.SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConductorProperties.class)
public class ConductorCoreConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConductorCoreConfiguration.class);

    @Bean
    public Lock provideLock() {
        return new com.netflix.conductor.core.sync.local.LocalOnlyLock();
    }

    @ConditionalOnProperty(
            name = "conductor.external-payload-storage.type",
            havingValue = "dummy",
            matchIfMissing = true)
    @Bean
    public ExternalPayloadStorage dummyExternalPayloadStorage() {
        LOGGER.info("Initialized dummy payload storage!");
        return new DummyPayloadStorage();
    }

    @ConditionalOnProperty(
            name = "conductor.workflow-status-listener.type",
            havingValue = "stub",
            matchIfMissing = true)
    @Bean
    public WorkflowStatusListener workflowStatusListener() {
        return new WorkflowStatusListenerStub();
    }

    @ConditionalOnProperty(
            name = "conductor.task-status-listener.type",
            havingValue = "stub",
            matchIfMissing = true)
    @Bean
    public TaskStatusListener taskStatusListener() {
        return new TaskStatusListenerStub();
    }

    @ConditionalOnProperty(
            name = "conductor.metadata-change-listener.type",
            havingValue = "stub",
            matchIfMissing = true)
    @Bean
    public MetadataChangeListener metadataChangeListener() {
        return new MetadataChangeListenerStub();
    }

    @Bean
    public ExecutorService executorService(ConductorProperties conductorProperties) {
        ThreadFactory threadFactory =
                new BasicThreadFactory.Builder()
                        .namingPattern("conductor-worker-%d")
                        .daemon(true)
                        .build();
        return Executors.newFixedThreadPool(
                conductorProperties.getExecutorServiceMaxThreadCount(), threadFactory);
    }

    @Bean("taskMappersByTaskType")
    @Qualifier("taskMappersByTaskType")
    @jakarta.inject.Named("taskMappersByTaskType")
    public Map<String, TaskMapper> getTaskMappers(
            jakarta.enterprise.inject.Instance<TaskMapper> taskMappers) {
        Map<String, TaskMapper> map = new java.util.HashMap<>();
        for (TaskMapper tm : taskMappers) {
            map.putIfAbsent(tm.getTaskType(), tm);
        }
        return map;
    }

    @Bean(ASYNC_SYSTEM_TASKS_QUALIFIER)
    @Qualifier(ASYNC_SYSTEM_TASKS_QUALIFIER)
    @jakarta.inject.Named(ASYNC_SYSTEM_TASKS_QUALIFIER)
    public Set<WorkflowSystemTask> asyncSystemTasks(
            jakarta.enterprise.inject.Instance<WorkflowSystemTask> allSystemTasks) {
        Set<WorkflowSystemTask> set = new java.util.HashSet<>();
        for (WorkflowSystemTask t : allSystemTasks) {
            if (t.isAsync()) {
                set.add(t);
            }
        }
        return set;
    }

    @Bean(EVENT_QUEUE_PROVIDERS_QUALIFIER)
    @Qualifier(EVENT_QUEUE_PROVIDERS_QUALIFIER)
    @jakarta.inject.Named(EVENT_QUEUE_PROVIDERS_QUALIFIER)
    public Map<String, EventQueueProvider> getEventQueueProviders(
            jakarta.enterprise.inject.Instance<EventQueueProvider> eventQueueProviders) {
        Map<String, EventQueueProvider> map = new java.util.HashMap<>();
        for (EventQueueProvider p : eventQueueProviders) {
            map.put(p.getQueueType(), p);
        }
        return map;
    }

    @Bean
    @ConditionalOnMissingBean(IDGenerator.class)
    public IDGenerator idGenerator() {
        return new IDGenerator();
    }

    @Bean
    public RetryTemplate onTransientErrorRetryTemplate() {
        return RetryTemplate.builder()
                .retryOn(TransientException.class)
                .maxAttempts(3)
                .noBackoff()
                .build();
    }
}
