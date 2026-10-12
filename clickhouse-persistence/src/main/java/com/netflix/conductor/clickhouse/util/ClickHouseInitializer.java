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
package com.netflix.conductor.clickhouse.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.clickhouse.config.ClickHouseProperties;

public final class ClickHouseInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClickHouseInitializer.class);

    private ClickHouseInitializer() {}

    /**
     * Ensures that the target database specified in the properties/URL exists. Connects to the
     * ClickHouse server to run 'CREATE DATABASE IF NOT EXISTS <database>'.
     */
    public static void ensureDatabaseExists(ClickHouseProperties properties) {
        String dbName = properties.getDatabase();
        if (dbName == null || dbName.isBlank()) {
            return;
        }

        String url = properties.getUrl();
        if (url == null || url.isBlank()) {
            return;
        }

        try {
            int queryIdx = url.indexOf('?');
            String pathPart = (queryIdx != -1) ? url.substring(0, queryIdx) : url;
            int lastSlash = pathPart.lastIndexOf('/');
            String queryParams = (queryIdx != -1) ? url.substring(queryIdx) : "";

            // Base server URL without database name, e.g. jdbc:clickhouse://host:port/
            String baseServerUrl =
                    (lastSlash != -1)
                            ? pathPart.substring(0, lastSlash + 1) + queryParams
                            : pathPart + "/" + queryParams;

            try (Connection conn =
                            DriverManager.getConnection(
                                    baseServerUrl,
                                    properties.getUsername(),
                                    properties.getPassword());
                    Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE IF NOT EXISTS " + dbName);
                LOGGER.info("Ensured ClickHouse database '{}' exists.", dbName);
            }
        } catch (Exception e) {
            LOGGER.warn(
                    "Could not automatically ensure ClickHouse database '{}' existence: {}",
                    dbName,
                    e.getMessage());
        }
    }
}
