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
package com.netflix.conductor.couchdb.config;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "conductor.couchdb")
public class CouchDbProperties {

    /** CouchDB server URL, e.g. http://localhost:5984 */
    private String url = "http://localhost:5984";

    /** CouchDB username */
    private String username = "admin";

    /** CouchDB password */
    private String password = "password";

    /** Database prefix for conductor tables */
    private String dbPrefix = "conductor_";

    /** Connection timeout in seconds */
    private int connectionTimeoutSeconds = 10;

    /** Read timeout in seconds */
    private int readTimeoutSeconds = 30;

    public CouchDbProperties() {
        try {
            Config config = ConfigProvider.getConfig();
            this.url =
                    config.getOptionalValue("conductor.couchdb.url", String.class)
                            .or(() -> config.getOptionalValue("couchdb.url", String.class))
                            .or(() -> config.getOptionalValue("COUCHDB_URL", String.class))
                            .orElse(this.url);

            this.username =
                    config.getOptionalValue("conductor.couchdb.username", String.class)
                            .or(() -> config.getOptionalValue("couchdb.username", String.class))
                            .or(() -> config.getOptionalValue("COUCHDB_USERNAME", String.class))
                            .or(() -> config.getOptionalValue("COUCHDB_USER", String.class))
                            .orElse(this.username);

            this.password =
                    config.getOptionalValue("conductor.couchdb.password", String.class)
                            .or(() -> config.getOptionalValue("couchdb.password", String.class))
                            .or(() -> config.getOptionalValue("COUCHDB_PASSWORD", String.class))
                            .orElse(this.password);

            this.dbPrefix =
                    config.getOptionalValue("conductor.couchdb.dbPrefix", String.class)
                            .orElse(this.dbPrefix);

            this.connectionTimeoutSeconds =
                    config.getOptionalValue(
                                    "conductor.couchdb.connectionTimeoutSeconds", Integer.class)
                            .orElse(this.connectionTimeoutSeconds);

            this.readTimeoutSeconds =
                    config.getOptionalValue("conductor.couchdb.readTimeoutSeconds", Integer.class)
                            .orElse(this.readTimeoutSeconds);
        } catch (Throwable ignored) {
            String envUrl =
                    System.getProperty("conductor.couchdb.url", System.getenv("COUCHDB_URL"));
            if (envUrl != null && !envUrl.isBlank()) {
                this.url = envUrl;
            }
            String envUser =
                    System.getProperty(
                            "conductor.couchdb.username", System.getenv("COUCHDB_USERNAME"));
            if (envUser == null || envUser.isBlank()) {
                envUser = System.getenv("COUCHDB_USER");
            }
            if (envUser != null && !envUser.isBlank()) {
                this.username = envUser;
            }
            String envPass =
                    System.getProperty(
                            "conductor.couchdb.password", System.getenv("COUCHDB_PASSWORD"));
            if (envPass != null) {
                this.password = envPass;
            }
        }
    }

    public String getUrl() {
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

    public String getDbPrefix() {
        return dbPrefix;
    }

    public void setDbPrefix(String dbPrefix) {
        this.dbPrefix = dbPrefix;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }
}
