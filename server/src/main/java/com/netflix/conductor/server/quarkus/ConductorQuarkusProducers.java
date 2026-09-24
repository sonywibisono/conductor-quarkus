/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.server.quarkus;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.conductoross.conductor.service.SchemaCacheProperties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.events.EventQueueManager;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.execution.evaluators.GraalJSEvaluator;
import com.netflix.conductor.core.execution.evaluators.JavascriptEvaluator;
import com.netflix.conductor.core.execution.evaluators.PythonEvaluator;
import com.netflix.conductor.core.execution.evaluators.ValueParamEvaluator;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.reconciliation.WorkflowRepairService;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.model.TaskModel.Status;
import com.netflix.conductor.sqlite.config.SqliteProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@ApplicationScoped
public class ConductorQuarkusProducers {

    private static final Logger log = LoggerFactory.getLogger(ConductorQuarkusProducers.class);

    @Inject DataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        log.info("Initializing SQLite database connection and PRAGMAs...");
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA busy_timeout=30000");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA temp_store=MEMORY");
        } catch (Exception e) {
            log.warn("Failed to set SQLite PRAGMAs: {}", e.getMessage());
        }

        try {
            log.info("Running Flyway migrations for SQLite...");
            FluentConfiguration config =
                    Flyway.configure()
                            .dataSource(dataSource)
                            .locations("classpath:db/migration_sqlite")
                            .sqlMigrationPrefix("V")
                            .sqlMigrationSeparator("__")
                            .mixed(true)
                            .validateOnMigrate(true)
                            .baselineOnMigrate(true)
                            .baselineVersion("0");
            Flyway flyway = new Flyway(config);
            flyway.migrate();
            log.info("SQLite database migrated successfully!");
        } catch (Exception e) {
            log.warn("Flyway migration exception: {}", e.getMessage(), e);
        }
    }

    @Produces
    @Singleton
    public ObjectMapper objectMapper() {
        return new ObjectMapperProvider().getObjectMapper();
    }

    @Produces
    @Singleton
    public ConductorProperties conductorProperties() {
        return new ConductorProperties();
    }

    @Produces
    @Singleton
    public SqliteProperties sqliteProperties() {
        return new SqliteProperties();
    }

    @Produces
    @Singleton
    public WorkflowMessageQueueProperties workflowMessageQueueProperties() {
        return new WorkflowMessageQueueProperties();
    }

    @Produces
    @Singleton
    public Map<String, Evaluator> evaluators(Instance<Evaluator> evaluators) {
        Map<String, Evaluator> map = new HashMap<>();
        for (Evaluator e : evaluators) {
            if (e instanceof ValueParamEvaluator) {
                map.put(ValueParamEvaluator.NAME, e);
            } else if (e instanceof JavascriptEvaluator) {
                map.put(JavascriptEvaluator.NAME, e);
            } else if (e instanceof GraalJSEvaluator) {
                map.put(GraalJSEvaluator.NAME, e);
            } else if (e instanceof PythonEvaluator) {
                map.put(PythonEvaluator.NAME, e);
            } else {
                map.put(e.getClass().getSimpleName(), e);
            }
        }
        return map;
    }

    @Produces
    @Singleton
    public Map<Status, ObservableQueue> defaultQueues() {
        return Collections.emptyMap();
    }

    @Produces
    @Singleton
    @Named("annotatedTaskSystems")
    public Map<String, TaskMapper> annotatedTaskSystems() {
        return new HashMap<>();
    }

    @Produces
    @Singleton
    public List<AnnotatedSystemTaskWorker> annotatedSystemTaskWorkers() {
        return Collections.emptyList();
    }

    @Produces
    @Singleton
    public JsonSchemaValidator jsonSchemaValidator(ObjectMapper objectMapper) {
        return new JsonSchemaValidator(objectMapper);
    }

    @Produces
    @Singleton
    public SchemaCacheProperties schemaCacheProperties() {
        return new SchemaCacheProperties();
    }

    @Produces
    @Singleton
    public MeterRegistry[] meterRegistries(MeterRegistry meterRegistry) {
        return new MeterRegistry[] {meterRegistry};
    }

    @Produces
    @Singleton
    public Optional<WorkflowRepairService> workflowRepairService(
            Instance<WorkflowRepairService> service) {
        return service.isResolvable() ? Optional.of(service.get()) : Optional.empty();
    }

    @Produces
    @Singleton
    public Optional<EventQueueManager> eventQueueManager(Instance<EventQueueManager> manager) {
        return manager.isResolvable() ? Optional.of(manager.get()) : Optional.empty();
    }

    @Produces
    @Singleton
    public Optional<WorkflowMessageQueueDAO> workflowMessageQueueDAO(
            Instance<WorkflowMessageQueueDAO> dao) {
        return dao.isResolvable() ? Optional.of(dao.get()) : Optional.empty();
    }
}
