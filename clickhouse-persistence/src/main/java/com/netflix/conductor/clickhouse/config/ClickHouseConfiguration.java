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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.clickhouse.dao.ClickHouseExecutionDAO;
import com.netflix.conductor.clickhouse.dao.ClickHouseMetadataDAO;
import com.netflix.conductor.dao.ConcurrentExecutionLimitDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.dao.RateLimitingDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Configuration for ClickHouse primary persistence (Metadata, Execution, PollData). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "clickhouse")
@io.quarkus.arc.properties.IfBuildProperty(name = "conductor.db.type", stringValue = "clickhouse")
public class ClickHouseConfiguration {

    @Bean
    public ClickHouseProperties clickHouseProperties() {
        return new ClickHouseProperties();
    }

    @Bean
    public MetadataDAO clickHouseMetadataDAO(
            ClickHouseProperties properties, ObjectMapper objectMapper) {
        return new ClickHouseMetadataDAO(properties, objectMapper);
    }

    @Bean
    public ExecutionDAO clickHouseExecutionDAO(
            ClickHouseProperties properties, ObjectMapper objectMapper) {
        return new ClickHouseExecutionDAO(properties, objectMapper);
    }

    @Bean
    public RateLimitingDAO clickHouseRateLimitingDAO(ExecutionDAO executionDAO) {
        return (RateLimitingDAO) executionDAO;
    }

    @Bean
    public ConcurrentExecutionLimitDAO clickHouseConcurrentExecutionLimitDAO(
            ExecutionDAO executionDAO) {
        return (ConcurrentExecutionLimitDAO) executionDAO;
    }

    @Bean
    public PollDataDAO clickHousePollDataDAO(ExecutionDAO executionDAO) {
        return (PollDataDAO) executionDAO;
    }

    @Bean
    public com.netflix.conductor.dao.EventHandlerDAO clickHouseEventHandlerDAO(
            MetadataDAO metadataDAO) {
        return (com.netflix.conductor.dao.EventHandlerDAO) metadataDAO;
    }
}
