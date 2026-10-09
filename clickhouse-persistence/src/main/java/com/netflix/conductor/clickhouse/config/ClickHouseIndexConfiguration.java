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

import com.netflix.conductor.clickhouse.dao.ClickHouseIndexDAO;
import com.netflix.conductor.dao.IndexDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "clickhouse")
@io.quarkus.arc.properties.IfBuildProperty(
        name = "conductor.indexing.type",
        stringValue = "clickhouse")
public class ClickHouseIndexConfiguration {

    @Bean
    public ClickHouseProperties clickHouseProperties() {
        return new ClickHouseProperties();
    }

    @Bean
    public IndexDAO clickHouseIndexDAO(ClickHouseProperties properties, ObjectMapper objectMapper) {
        return new ClickHouseIndexDAO(properties, objectMapper);
    }
}
