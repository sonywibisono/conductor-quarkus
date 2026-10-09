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

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.couchdb.client.CouchDbClient;
import com.netflix.conductor.couchdb.config.CouchDbProperties;
import com.netflix.conductor.dao.EventHandlerDAO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** CouchDB implementation of EventHandlerDAO. */
public class CouchDbEventHandlerDAO implements EventHandlerDAO {

    private final CouchDbClient client;
    private final ObjectMapper objectMapper;
    private final String dbName;

    public CouchDbEventHandlerDAO(
            CouchDbClient client, ObjectMapper objectMapper, CouchDbProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.dbName = properties.getDbPrefix() + "event_handlers";
        this.client.ensureDatabaseExists(this.dbName);
    }

    private String getDocId(String name) {
        return "event_handler_" + name;
    }

    @Override
    public void addEventHandler(EventHandler eventHandler) {
        Optional<EventHandler> existing =
                client.getDocument(dbName, getDocId(eventHandler.getName()), EventHandler.class);
        if (existing.isPresent()) {
            throw new ConflictException(
                    "EventHandler with name " + eventHandler.getName() + " already exists!");
        }
        client.putDocument(dbName, getDocId(eventHandler.getName()), eventHandler);
    }

    @Override
    public void updateEventHandler(EventHandler eventHandler) {
        client.putDocument(dbName, getDocId(eventHandler.getName()), eventHandler);
    }

    @Override
    public void removeEventHandler(String name) {
        client.deleteDocument(dbName, getDocId(name));
    }

    @Override
    public List<EventHandler> getAllEventHandlers() {
        Map<String, Object> query =
                Map.of(
                        "selector",
                        Map.of("_id", Map.of("$regex", "^event_handler_")),
                        "limit",
                        10000);
        JsonNode result = client.find(dbName, query);
        List<EventHandler> list = new ArrayList<>();
        if (result.has("docs")) {
            for (JsonNode doc : result.get("docs")) {
                try {
                    list.add(objectMapper.treeToValue(doc, EventHandler.class));
                } catch (Exception ignored) {
                }
            }
        }
        return list;
    }

    @Override
    public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
        return getAllEventHandlers().stream()
                .filter(eh -> event.equals(eh.getEvent()))
                .filter(eh -> !activeOnly || eh.isActive())
                .collect(Collectors.toList());
    }
}
