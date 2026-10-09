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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.couchdb.client.CouchDbClient;
import com.netflix.conductor.couchdb.dao.CouchDbEventHandlerDAO;
import com.netflix.conductor.couchdb.dao.CouchDbExecutionDAO;
import com.netflix.conductor.couchdb.dao.CouchDbMetadataDAO;
import com.netflix.conductor.couchdb.dao.CouchDbPollDataDAO;
import com.netflix.conductor.dao.ConcurrentExecutionLimitDAO;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.dao.RateLimitingDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring configuration class for Apache CouchDB persistence. Activated when conductor.db.type is
 * set to "couchdb".
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "couchdb")
@io.quarkus.arc.properties.IfBuildProperty(name = "conductor.db.type", stringValue = "couchdb")
public class CouchDbConfiguration {

    @Bean
    public CouchDbProperties couchDbProperties() {
        return new CouchDbProperties();
    }

    @Bean
    public CouchDbClient couchDbClient(CouchDbProperties properties, ObjectMapper objectMapper) {
        return new CouchDbClient(properties, objectMapper);
    }

    @Bean
    public MetadataDAO couchDbMetadataDAO(
            CouchDbClient client, ObjectMapper objectMapper, CouchDbProperties properties) {
        return new CouchDbMetadataDAO(client, objectMapper, properties);
    }

    @Bean
    public ExecutionDAO couchDbExecutionDAO(
            CouchDbClient client, ObjectMapper objectMapper, CouchDbProperties properties) {
        return new CouchDbExecutionDAO(client, objectMapper, properties);
    }

    @Bean
    public RateLimitingDAO couchDbRateLimitingDAO(ExecutionDAO executionDAO) {
        return (RateLimitingDAO) executionDAO;
    }

    @Bean
    public ConcurrentExecutionLimitDAO couchDbConcurrentExecutionLimitDAO(
            ExecutionDAO executionDAO) {
        return (ConcurrentExecutionLimitDAO) executionDAO;
    }

    @Bean
    public PollDataDAO couchDbPollDataDAO(
            CouchDbClient client, ObjectMapper objectMapper, CouchDbProperties properties) {
        return new CouchDbPollDataDAO(client, objectMapper, properties);
    }

    @Bean
    public EventHandlerDAO couchDbEventHandlerDAO(
            CouchDbClient client, ObjectMapper objectMapper, CouchDbProperties properties) {
        return new CouchDbEventHandlerDAO(client, objectMapper, properties);
    }
}
