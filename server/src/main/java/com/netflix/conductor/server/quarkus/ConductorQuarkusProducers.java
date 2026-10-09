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
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.core.execution.WorkflowSweeper;
import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.conductoross.conductor.service.SchemaCacheProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
import com.netflix.conductor.core.execution.tasks.SystemTaskWorker;
import com.netflix.conductor.core.execution.tasks.SystemTaskWorkerCoordinator;
import com.netflix.conductor.core.reconciliation.WorkflowRepairService;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.model.TaskModel.Status;

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

    @Inject
    @ConfigProperty(name = "conductor.db.type", defaultValue = "redis_standalone")
    String dbType;

    void onStart(
            @Observes StartupEvent ev,
            SystemTaskWorkerCoordinator systemTaskWorkerCoordinator,
            SystemTaskWorker systemTaskWorker,
            WorkflowSweeper workflowSweeper) {
        if (!"couchdb".equalsIgnoreCase(dbType) && !"clickhouse".equalsIgnoreCase(dbType)) {
            try (Connection conn = dataSource.getConnection()) {
                String dbProductName = conn.getMetaData().getDatabaseProductName();
                log.info("Detected database product: {}", dbProductName);

                if (dbProductName != null && dbProductName.toLowerCase().contains("sqlite")) {
                    log.info("Initializing SQLite database connection and PRAGMAs...");
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("PRAGMA journal_mode=WAL");
                        stmt.execute("PRAGMA busy_timeout=30000");
                        stmt.execute("PRAGMA foreign_keys=ON");
                        stmt.execute("PRAGMA synchronous=NORMAL");
                        stmt.execute("PRAGMA temp_store=MEMORY");
                    } catch (Exception e) {
                        log.warn("Failed to set SQLite PRAGMAs: {}", e.getMessage());
                    }

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
                } else if (dbProductName != null
                        && dbProductName.toLowerCase().contains("postgres")) {
                    log.info("Running Flyway migrations for PostgreSQL...");
                    FluentConfiguration config =
                            Flyway.configure()
                                    .dataSource(dataSource)
                                    .locations(
                                            "classpath:db/migration_postgres",
                                            "classpath:db/migration_postgres_data")
                                    .configuration(
                                            java.util.Map.of(
                                                    "flyway.postgresql.transactional.lock",
                                                    "false"))
                                    .outOfOrder(true)
                                    .sqlMigrationPrefix("V")
                                    .sqlMigrationSeparator("__")
                                    .mixed(true)
                                    .validateOnMigrate(true)
                                    .baselineOnMigrate(true)
                                    .baselineVersion("0");
                    Flyway flyway = new Flyway(config);
                    flyway.migrate();
                    log.info("PostgreSQL database migrated successfully!");
                }
            } catch (Exception e) {
                log.error("Flyway migration exception: {}", e.getMessage(), e);
            }
        }

        systemTaskWorker.start();
        workflowSweeper.start();
        systemTaskWorkerCoordinator.initSystemTaskExecutor();
    }

    @Produces
    @Singleton
    @io.quarkus.arc.DefaultBean
    public com.netflix.conductor.dao.IndexDAO defaultIndexDAO() {
        return new com.netflix.conductor.core.index.NoopIndexDAO();
    }

    @Produces
    @Singleton
    @io.quarkus.arc.DefaultBean
    public com.netflix.conductor.dao.QueueDAO defaultQueueDAO() {
        return new LocalInMemoryQueueDAO();
    }

    @Produces
    @Singleton
    @io.quarkus.arc.DefaultBean
    public org.conductoross.conductor.dao.schema.SchemaDAO defaultSchemaDAO() {
        return new org.conductoross.conductor.dao.schema.InMemorySchemaDAO();
    }

    @Produces
    @Singleton
    public ObjectMapper objectMapper() {
        return new ObjectMapperProvider().getObjectMapper();
    }

    @Produces
    @Singleton
    public ConductorProperties conductorProperties(
            @ConfigProperty(name = "conductor.app.sweeperThreadCount", defaultValue = "64")
                    int sweeperThreads,
            @ConfigProperty(
                            name = "conductor.app.sweeperWorkflowPollTimeout",
                            defaultValue = "1000")
                    Duration pollTimeout) {
        ConductorProperties props = new ConductorProperties();
        props.setSweeperThreadCount(sweeperThreads);
        props.setSweeperWorkflowPollTimeout(pollTimeout);
        return props;
    }

    @Produces
    @Singleton
    public com.netflix.conductor.postgres.config.PostgresProperties postgresProperties() {
        return new com.netflix.conductor.postgres.config.PostgresProperties();
    }

    @Produces
    @Singleton
    @io.quarkus.arc.DefaultBean
    public com.netflix.conductor.core.sync.Lock defaultLock() {
        return new com.netflix.conductor.core.sync.local.LocalOnlyLock();
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

    void setupSpaRouting(@Observes io.vertx.ext.web.Router router) {
        router.route()
                .order(100)
                .handler(
                        rc -> {
                            String path = rc.request().path();
                            if (rc.request().method() == io.vertx.core.http.HttpMethod.GET
                                    && !path.startsWith("/api")
                                    && !path.startsWith("/q")
                                    && !path.startsWith("/swagger-ui")
                                    && !path.contains(".")) {
                                rc.reroute("/index.html");
                            } else {
                                rc.next();
                            }
                        });
    }

    static class LocalInMemoryQueueDAO implements com.netflix.conductor.dao.QueueDAO {
        private final java.util.concurrent.ConcurrentHashMap<
                        String, java.util.concurrent.LinkedBlockingDeque<String>>
                queues = new java.util.concurrent.ConcurrentHashMap<>();

        private java.util.concurrent.LinkedBlockingDeque<String> q(String name) {
            return queues.computeIfAbsent(
                    name, k -> new java.util.concurrent.LinkedBlockingDeque<>());
        }

        @Override
        public void push(String queueName, String id, long offsetTimeInSecond) {
            q(queueName).addLast(id);
        }

        @Override
        public void push(String queueName, String id, int priority, long offsetTimeInSecond) {
            q(queueName).addLast(id);
        }

        @Override
        public void push(
                String queueName,
                java.util.List<com.netflix.conductor.core.events.queue.Message> messages) {
            messages.forEach(m -> q(queueName).addLast(m.getId()));
        }

        @Override
        public boolean pushIfNotExists(String queueName, String id, long offsetTimeInSecond) {
            var queue = q(queueName);
            if (queue.contains(id)) return false;
            queue.addLast(id);
            return true;
        }

        @Override
        public boolean pushIfNotExists(
                String queueName, String id, int priority, long offsetTimeInSecond) {
            return pushIfNotExists(queueName, id, offsetTimeInSecond);
        }

        @Override
        public java.util.List<String> pop(String queueName, int count, int timeout) {
            var queue = q(queueName);
            java.util.List<String> result = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = queue.poll();
                if (id == null) break;
                result.add(id);
            }
            if (result.isEmpty() && timeout > 0) {
                try {
                    String id =
                            queue.poll(
                                    Math.min(timeout, 200),
                                    java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (id != null) result.add(id);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return result;
        }

        @Override
        public java.util.List<com.netflix.conductor.core.events.queue.Message> pollMessages(
                String queueName, int count, int timeout) {
            return pop(queueName, count, timeout).stream()
                    .map(id -> new com.netflix.conductor.core.events.queue.Message(id, id, null))
                    .toList();
        }

        @Override
        public void remove(String queueName, String messageId) {
            q(queueName).remove(messageId);
        }

        @Override
        public int getSize(String queueName) {
            return q(queueName).size();
        }

        @Override
        public boolean ack(String queueName, String messageId) {
            return q(queueName).remove(messageId);
        }

        @Override
        public boolean setUnackTimeout(String queueName, String messageId, long unackTimeout) {
            return true;
        }

        @Override
        public void flush(String queueName) {
            q(queueName).clear();
        }

        @Override
        public boolean resetOffsetTime(String queueName, String id) {
            return true;
        }

        @Override
        public java.util.Map<String, Long> queuesDetail() {
            java.util.Map<String, Long> map = new java.util.HashMap<>();
            queues.forEach((k, v) -> map.put(k, (long) v.size()));
            return map;
        }

        @Override
        public java.util.Map<String, java.util.Map<String, java.util.Map<String, Long>>>
                queuesDetailVerbose() {
            return java.util.Collections.emptyMap();
        }
    }
}
