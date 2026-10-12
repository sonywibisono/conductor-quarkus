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
package com.netflix.conductor.clickhouse.config;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "conductor.clickhouse")
public class ClickHouseProperties {

    /** ClickHouse JDBC URL, e.g. jdbc:ch://localhost:8123/conductor */
    private String url = "jdbc:ch://localhost:8123/default";

    /** ClickHouse username */
    private String username = "admin";

    /** ClickHouse password */
    private String password = "admin123";

    /** ClickHouse database name */
    private String database;

    /** Number of worker threads for async indexing */
    private int asyncWorkers = 4;

    /** Queue capacity for async indexing operations */
    private int asyncQueueCapacity = 10000;

    /** Whether to auto-initialize table schemas on startup */
    private boolean autoInit = true;

    public ClickHouseProperties() {
        try {
            Config config = ConfigProvider.getConfig();
            this.url =
                    config.getOptionalValue("conductor.clickhouse.url", String.class)
                            .or(() -> config.getOptionalValue("clickhouse.url", String.class))
                            .or(() -> config.getOptionalValue("CLICKHOUSE_URL", String.class))
                            .orElse(this.url);

            this.username =
                    config.getOptionalValue("conductor.clickhouse.username", String.class)
                            .or(() -> config.getOptionalValue("clickhouse.user", String.class))
                            .or(() -> config.getOptionalValue("clickhouse.username", String.class))
                            .or(() -> config.getOptionalValue("CLICKHOUSE_USERNAME", String.class))
                            .or(() -> config.getOptionalValue("CLICKHOUSE_USER", String.class))
                            .orElse(this.username);

            this.password =
                    config.getOptionalValue("conductor.clickhouse.password", String.class)
                            .or(() -> config.getOptionalValue("clickhouse.password", String.class))
                            .or(() -> config.getOptionalValue("CLICKHOUSE_PASSWORD", String.class))
                            .orElse(this.password);

            this.database =
                    config.getOptionalValue("conductor.clickhouse.database", String.class)
                            .or(() -> config.getOptionalValue("clickhouse.database", String.class))
                            .or(() -> config.getOptionalValue("CLICKHOUSE_DB", String.class))
                            .orElseGet(this::extractDatabaseFromUrl);

            this.asyncWorkers =
                    config.getOptionalValue("conductor.clickhouse.asyncWorkers", Integer.class)
                            .orElse(this.asyncWorkers);

            this.asyncQueueCapacity =
                    config.getOptionalValue(
                                    "conductor.clickhouse.asyncQueueCapacity", Integer.class)
                            .orElse(this.asyncQueueCapacity);

            this.autoInit =
                    config.getOptionalValue("conductor.clickhouse.autoInit", Boolean.class)
                            .orElse(this.autoInit);
        } catch (Throwable ignored) {
            String envUrl =
                    System.getProperty("conductor.clickhouse.url", System.getenv("CLICKHOUSE_URL"));
            if (envUrl != null && !envUrl.isBlank()) {
                this.url = envUrl;
            }
            String envUser =
                    System.getProperty(
                            "conductor.clickhouse.username", System.getenv("CLICKHOUSE_USERNAME"));
            if (envUser == null || envUser.isBlank()) {
                envUser = System.getenv("CLICKHOUSE_USER");
            }
            if (envUser != null && !envUser.isBlank()) {
                this.username = envUser;
            }
            String envPass =
                    System.getProperty(
                            "conductor.clickhouse.password", System.getenv("CLICKHOUSE_PASSWORD"));
            if (envPass != null) {
                this.password = envPass;
            }
            String envDb =
                    System.getProperty(
                            "conductor.clickhouse.database", System.getenv("CLICKHOUSE_DB"));
            if (envDb == null || envDb.isBlank()) {
                envDb = System.getProperty("clickhouse.database");
            }
            if (envDb != null && !envDb.isBlank()) {
                this.database = envDb;
            } else {
                this.database = extractDatabaseFromUrl();
            }
        }
    }

    public String extractDatabaseFromUrl() {
        if (this.url == null || this.url.isBlank()) {
            return "conductor";
        }
        try {
            int queryIdx = this.url.indexOf('?');
            String pathPart = (queryIdx != -1) ? this.url.substring(0, queryIdx) : this.url;
            int lastSlash = pathPart.lastIndexOf('/');
            if (lastSlash != -1 && lastSlash < pathPart.length() - 1) {
                String db = pathPart.substring(lastSlash + 1).trim();
                if (!db.isEmpty()) {
                    return db;
                }
            }
        } catch (Exception ignored) {
        }
        return "conductor";
    }

    public String getDatabase() {
        if (database == null || database.isBlank()) {
            database = extractDatabaseFromUrl();
        }
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUrl() {
        if (url != null && !url.isBlank() && !url.contains("compress=")) {
            return url.contains("?") ? url + "&compress=0" : url + "?compress=0";
        }
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getAsyncWorkers() {
        return asyncWorkers;
    }

    public void setAsyncWorkers(int asyncWorkers) {
        this.asyncWorkers = asyncWorkers;
    }

    public int getAsyncQueueCapacity() {
        return asyncQueueCapacity;
    }

    public void setAsyncQueueCapacity(int asyncQueueCapacity) {
        this.asyncQueueCapacity = asyncQueueCapacity;
    }

    public boolean isAutoInit() {
        return autoInit;
    }

    public void setAutoInit(boolean autoInit) {
        this.autoInit = autoInit;
    }
}
