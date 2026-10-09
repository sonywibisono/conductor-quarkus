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

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.couchdb.client.CouchDbClient;
import com.netflix.conductor.couchdb.config.CouchDbProperties;
import com.netflix.conductor.dao.PollDataDAO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** CouchDB implementation of PollDataDAO. */
public class CouchDbPollDataDAO implements PollDataDAO {

    private final CouchDbClient client;
    private final ObjectMapper objectMapper;
    private final String dbName;

    public CouchDbPollDataDAO(
            CouchDbClient client, ObjectMapper objectMapper, CouchDbProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.dbName = properties.getDbPrefix() + "poll_data";
        this.client.ensureDatabaseExists(this.dbName);
    }

    private String getDocId(String taskDefName, String domain) {
        return "poll_" + taskDefName + "_" + (domain == null ? "default" : domain);
    }

    @Override
    public void updateLastPollData(String taskDefName, String domain, String workerId) {
        PollData pollData = new PollData(taskDefName, domain, workerId, System.currentTimeMillis());
        client.putDocument(dbName, getDocId(taskDefName, domain), pollData);
    }

    @Override
    public PollData getPollData(String taskDefName, String domain) {
        return client.getDocument(dbName, getDocId(taskDefName, domain), PollData.class)
                .orElse(null);
    }

    @Override
    public List<PollData> getPollData(String taskDefName) {
        Map<String, Object> query =
                Map.of(
                        "selector",
                        Map.of("_id", Map.of("$regex", "^poll_" + taskDefName + "_")),
                        "limit",
                        1000);
        JsonNode result = client.find(dbName, query);
        List<PollData> list = new ArrayList<>();
        if (result.has("docs")) {
            for (JsonNode doc : result.get("docs")) {
                try {
                    list.add(objectMapper.treeToValue(doc, PollData.class));
                } catch (Exception ignored) {
                }
            }
        }
        return list;
    }

    @Override
    public List<PollData> getAllPollData() {
        Map<String, Object> query =
                Map.of("selector", Map.of("_id", Map.of("$regex", "^poll_")), "limit", 10000);
        JsonNode result = client.find(dbName, query);
        List<PollData> list = new ArrayList<>();
        if (result.has("docs")) {
            for (JsonNode doc : result.get("docs")) {
                try {
                    list.add(objectMapper.treeToValue(doc, PollData.class));
                } catch (Exception ignored) {
                }
            }
        }
        return list;
    }
}
