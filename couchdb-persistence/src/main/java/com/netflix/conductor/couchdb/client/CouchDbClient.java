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
package com.netflix.conductor.couchdb.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.couchdb.config.CouchDbProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Lightweight HTTP client for Apache CouchDB operations using Java 21 native HttpClient. */
public class CouchDbClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CouchDbClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String authHeader;

    public CouchDbClient(CouchDbProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        String url = properties.getUrl();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;

        HttpClient.Builder builder =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(properties.getConnectionTimeoutSeconds()));
        this.httpClient = builder.build();

        if (properties.getUsername() != null && !properties.getUsername().isEmpty()) {
            String token = properties.getUsername() + ":" + properties.getPassword();
            this.authHeader =
                    "Basic "
                            + Base64.getEncoder()
                                    .encodeToString(token.getBytes(StandardCharsets.UTF_8));
        } else {
            this.authHeader = null;
        }
    }

    /** Creates a database if it does not already exist. */
    public void ensureDatabaseExists(String dbName) {
        try {
            HttpRequest.Builder req =
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/" + urlEncode(dbName)))
                            .PUT(HttpRequest.BodyPublishers.noBody());
            applyAuth(req);
            HttpResponse<String> resp =
                    httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 || resp.statusCode() == 201 || resp.statusCode() == 412) {
                LOGGER.debug("Database '{}' ready (HTTP {})", dbName, resp.statusCode());
            } else {
                LOGGER.warn(
                        "Unexpected response while ensuring database '{}': status={}, body={}",
                        dbName,
                        resp.statusCode(),
                        resp.body());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to ensure CouchDB database '{}' exists", dbName, e);
            throw new RuntimeException("CouchDB connection error for db " + dbName, e);
        }
    }

    /** Retrieves a document by id and maps it to the target type. */
    public <T> Optional<T> getDocument(String dbName, String id, Class<T> clazz) {
        try {
            HttpRequest.Builder req =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            baseUrl
                                                    + "/"
                                                    + urlEncode(dbName)
                                                    + "/"
                                                    + urlEncode(id)))
                            .GET();
            applyAuth(req);
            HttpResponse<String> resp =
                    httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) {
                return Optional.empty();
            }
            if (resp.statusCode() == 200) {
                return Optional.ofNullable(objectMapper.readValue(resp.body(), clazz));
            }
            LOGGER.warn(
                    "Error getting doc '{}' from '{}': status={}, body={}",
                    id,
                    dbName,
                    resp.statusCode(),
                    resp.body());
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.error("Failed to get document '{}' from db '{}'", id, dbName, e);
            throw new RuntimeException(e);
        }
    }

    /** Retrieves the raw JsonNode document. */
    public Optional<JsonNode> getJsonDocument(String dbName, String id) {
        return getDocument(dbName, id, JsonNode.class);
    }

    /** Upserts a document into CouchDB. Handles revision tracking for updates. */
    public void putDocument(String dbName, String id, Object document) {
        try {
            // First check if document exists to obtain latest _rev
            Optional<JsonNode> existing = getJsonDocument(dbName, id);
            Map<String, Object> docMap = objectMapper.convertValue(document, Map.class);
            docMap.put("_id", id);
            existing.ifPresent(
                    jsonNode -> {
                        if (jsonNode.has("_rev")) {
                            docMap.put("_rev", jsonNode.get("_rev").asText());
                        }
                    });

            String body = objectMapper.writeValueAsString(docMap);
            HttpRequest.Builder req =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            baseUrl
                                                    + "/"
                                                    + urlEncode(dbName)
                                                    + "/"
                                                    + urlEncode(id)))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            applyAuth(req);

            HttpResponse<String> resp =
                    httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 201) {
                throw new IllegalStateException(
                        "Failed to put document into "
                                + dbName
                                + "/"
                                + id
                                + " HTTP "
                                + resp.statusCode()
                                + ": "
                                + resp.body());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to put document '{}' into db '{}'", id, dbName, e);
            throw new RuntimeException(e);
        }
    }

    /** Deletes a document by id. */
    public boolean deleteDocument(String dbName, String id) {
        try {
            Optional<JsonNode> existing = getJsonDocument(dbName, id);
            if (existing.isEmpty()) {
                return false;
            }
            String rev = existing.get().get("_rev").asText();
            HttpRequest.Builder req =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            baseUrl
                                                    + "/"
                                                    + urlEncode(dbName)
                                                    + "/"
                                                    + urlEncode(id)
                                                    + "?rev="
                                                    + urlEncode(rev)))
                            .DELETE();
            applyAuth(req);

            HttpResponse<String> resp =
                    httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 || resp.statusCode() == 202;
        } catch (Exception e) {
            LOGGER.error("Failed to delete document '{}' from db '{}'", id, dbName, e);
            throw new RuntimeException(e);
        }
    }

    /** Executes a Mango query (_find endpoint). */
    public JsonNode find(String dbName, Object query) {
        try {
            String body = objectMapper.writeValueAsString(query);
            HttpRequest.Builder req =
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/" + urlEncode(dbName) + "/_find"))
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            body, StandardCharsets.UTF_8));
            applyAuth(req);

            HttpResponse<String> resp =
                    httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return objectMapper.readTree(resp.body());
            }
            throw new IllegalStateException(
                    "Mango find query failed on "
                            + dbName
                            + " HTTP "
                            + resp.statusCode()
                            + ": "
                            + resp.body());
        } catch (Exception e) {
            LOGGER.error("Failed to execute find query on '{}'", dbName, e);
            throw new RuntimeException(e);
        }
    }

    private void applyAuth(HttpRequest.Builder builder) {
        if (authHeader != null) {
            builder.header("Authorization", authHeader);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
