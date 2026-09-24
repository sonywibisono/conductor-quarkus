<picture>
  <!-- Dark mode logo -->
  <source srcset="https://github.com/user-attachments/assets/104b3a67-6013-4622-8075-a45da3a9e726" media="(prefers-color-scheme: dark)">
  <!-- Light mode logo -->
  <img src="https://assets.conductor-oss.org/logo.png" alt="Logo">
</picture>

<h1 align="center" style="border-bottom: none">
    Conductor Quarkus - Durable Workflow and Agent Orchestration
</h1>

<p align="center">
    <strong>High-performance, Cloud-Native Conductor Orchestration Engine powered by Quarkus</strong>
</p>

[![License](https://img.shields.io/github/license/conductor-oss/conductor.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Conductor OSS](https://img.shields.io/badge/Conductor%20OSS-Visit%20Site-blue)](https://conductor-oss.org)

#### Build agents that adapt. Run graphs that endure.

Conductor Quarkus is an open-source, cloud-native port of Netflix Conductor OSS built on top of **Quarkus 3.30.6** and Java 21+. It offers sub-second cold starts (~1.5s), drastically reduced memory overhead, native OpenAPI/Swagger UI, and live-reload development mode while preserving full REST API and workflow compatibility with Conductor OSS.

> **Note on UI Exclusion:** This repository contains the headless, API-first backend engine. All front-end assets (`ui` and `ui-next`) have been excluded. Management and inspection are conducted via the built-in Swagger UI, standard Conductor REST APIs, CLI, or polyglot SDKs.

---

# Key Features in Conductor Quarkus

| Feature | Description |
|---|---|
| **Ultra-Fast Startup** | Boots in ~1.5 seconds on JVM (compared to ~15-25 seconds on legacy stacks). |
| **Low Memory Footprint** | Optimized CDI Lite runtime via Quarkus ArC with zero Spring runtime container overhead. |
| **Live Coding & Dev Mode** | Near-instant hot reload of code and configuration changes via `./gradlew :conductor-server:quarkusDev`. |
| **Built-in SQLite Persistence** | Zero-configuration local database using Agroal and automated Flyway schema migrations. |
| **Standard Observability** | Native SmallRye Health (`/q/health`), Prometheus metrics (`/q/metrics`), and OpenAPI/Swagger UI (`/q/swagger-ui/`). |
| **Durable Execution** | Full support for Conductor tasks (`SIMPLE`, `SWITCH`, `DO_WHILE`, `FORK_JOIN`, `SUB_WORKFLOW`, `HTTP`, `INLINE`, etc.). |
| **Polyglot Workers** | Compatible with all official Conductor SDKs (Java, Python, Go, JavaScript, C#, Ruby, Rust). |

---

# Prerequisites

- **Java 21+** installed and configured on your `PATH`.
- **Git**.

---

# Quickstart

### 1. Build the Server

Run the Quarkus build task to compile all modules and produce the runnable application:

```shell
./gradlew :conductor-server:quarkusBuild
```

The output artifacts will be generated in `server/build/quarkus-app/`.

### 2. Run in Production Mode

Start the packaged Quarkus runner JAR:

```shell
java -jar server/build/quarkus-app/quarkus-run.jar
```

To specify a custom port:

```shell
java -Dquarkus.http.port=8080 -jar server/build/quarkus-app/quarkus-run.jar
```

### 3. Run in Development Mode (Live Coding)

To start the server with live reload and continuous testing enabled:

```shell
./gradlew :conductor-server:quarkusDev
```

Changes made to Java classes or configuration files are recompiled and reloaded automatically on incoming HTTP requests.

---

# Core Endpoints

Once the server is running on `http://localhost:8080`:

| Endpoint | Description |
|---|---|
| `GET /q/health` | Quarkus health check endpoint (validates database connection status). |
| `GET /q/swagger-ui/` | Interactive Swagger UI for browsing and testing REST APIs. |
| `GET /swagger-ui` | OpenAPI 3.1.0 JSON/YAML specification. |
| `GET /q/metrics` | Micrometer / Prometheus metrics. |
| `GET /api/metadata/workflow` | Conductor workflow definitions. |
| `GET /api/metadata/taskdefs` | Conductor task definitions. |
| `POST /api/workflow/{name}` | Start a workflow execution. |
| `GET /api/tasks/poll/{taskType}` | Poll pending tasks by worker. |

---

# Verifying the Installation

### Health Check

```shell
curl -i http://localhost:8080/q/health
```

Expected response:
```json
{
    "status": "UP",
    "checks": [
        {
            "name": "Database connections health check",
            "status": "UP",
            "data": {
                "<default>": "UP"
            }
        }
    ]
}
```

### Registering a Task Definition

```shell
curl -i -X POST http://localhost:8080/api/metadata/taskdefs \
  -H "Content-Type: application/json" \
  -d '[
    {
      "name": "sample_task",
      "ownerEmail": "developer@conductor.example.com",
      "retryCount": 3,
      "timeoutSeconds": 1200,
      "responseTimeoutSeconds": 600
    }
  ]'
```

### Registering a Workflow Definition

```shell
curl -i -X POST http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" \
  -d '{
    "name": "sample_workflow",
    "description": "Sample test workflow running on Conductor Quarkus",
    "version": 1,
    "ownerEmail": "developer@conductor.example.com",
    "tasks": [
      {
        "name": "sample_task",
        "taskReferenceName": "sample_task_ref_1",
        "type": "SIMPLE"
      }
    ],
    "timeoutSeconds": 3600
  }'
```

### Starting a Workflow Execution

```shell
curl -i -X POST "http://localhost:8080/api/workflow/sample_workflow" \
  -H "Content-Type: application/json" \
  -d '{"inputId": "test123"}'
```

Returns the generated workflow ID (e.g. `c346fdb3-6598-4520-b326-84d8896f72b2`).

### Polling and Completing Tasks

Worker polls for available task:
```shell
curl -i "http://localhost:8080/api/tasks/poll/sample_task?workerid=worker1"
```

Worker updates and completes the task:
```shell
curl -i -X POST "http://localhost:8080/api/tasks/<workflowId>/sample_task_ref_1/COMPLETED" \
  -H "Content-Type: application/json" \
  -d '{"outputId": "result123"}'
```

Retrieve final workflow status:
```shell
curl -i "http://localhost:8080/api/workflow/<workflowId>"
```

---

# Architecture & Module Structure

```text
conductor-quarkus/
├── core/                        # Core engine interfaces, domain models, and execution logic
├── common/                      # DTOs, metadata definitions, and Jackson configuration
├── rest/                        # REST resources and JAX-RS / Spring Web controllers
├── server/                      # Quarkus application entry point and CDI producers
│   └── src/main/java/.../quarkus/
│       └── ConductorQuarkusProducers.java   # Explicit CDI bean producers
├── sqlite-persistence/          # SQLite DAO implementation with Flyway migrations
└── ... (pluggable persistence and queue modules)
```

### Key Differences from Spring Boot Distribution

1. **Dependency Injection**: Replaced Spring Bean Factory with Quarkus ArC (CDI Lite). Bean wiring is resolved statically at build time.
2. **Spring Bridge Classes**: Provided lightweight bridge implementations (`BeanUtils`, `DataSize`, `Lifecycle`, `AttributeAccessor`, etc.) in `core` and `rest` to maintain source compatibility without importing the full Spring Framework.
3. **Database & Connection Pooling**: Managed via Agroal datasource and Flyway migrations rather than Spring Data / HikariCP.
4. **Front-End Exclusion**: The web UI packages have been excluded from the build lifecycle to maintain a lightweight, cloud-native container footprint.

---

# Development & Testing

### Running Tests

```shell
./gradlew :conductor-server:test
```

### Applying Code Formatting

Always run Spotless before submitting changes:

```shell
./gradlew spotlessApply
```

---

# License

Conductor is licensed under the [Apache 2.0 License](LICENSE).
