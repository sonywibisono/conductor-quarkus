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
package com.netflix.conductor.redis.config;

import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.conductoross.conductor.redis.dao.RedisSchemaDAO;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.ConcurrentExecutionLimitDAO;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.RateLimitingDAO;
import com.netflix.conductor.redis.dao.RedisEventHandlerDAO;
import com.netflix.conductor.redis.dao.RedisExecutionDAO;
import com.netflix.conductor.redis.dao.RedisMetadataDAO;
import com.netflix.conductor.redis.dao.RedisPollDataDAO;
import com.netflix.conductor.redis.dao.RedisRateLimitingDAO;
import com.netflix.conductor.redis.jedis.JedisCommands;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@io.quarkus.arc.properties.IfBuildProperty(
        name = "conductor.db.type",
        stringValue = "redis_standalone",
        enableIfMissing = true)
public class RedisPersistenceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisPersistenceConfiguration.class);

    @Bean
    @io.quarkus.arc.DefaultBean
    public RedisProperties redisProperties(ConductorProperties conductorProperties) {
        RedisProperties props = new RedisProperties(conductorProperties);
        try {
            Config config = ConfigProvider.getConfig();
            config.getOptionalValue("conductor.redis.hosts", String.class)
                    .or(() -> config.getOptionalValue("REDIS_HOSTS", String.class))
                    .ifPresent(props::setHosts);
            config.getOptionalValue("conductor.redis.workflowNamespacePrefix", String.class)
                    .ifPresent(props::setWorkflowNamespacePrefix);
            config.getOptionalValue("conductor.redis.queueNamespacePrefix", String.class)
                    .ifPresent(props::setQueueNamespacePrefix);
        } catch (Throwable t) {
            log.debug("Could not read Redis config from MicroProfile Config Provider", t);
        }
        return props;
    }

    @Bean
    @io.quarkus.arc.DefaultBean
    public JedisProxy jedisProxy(JedisCommands jedisCommands) {
        return new JedisProxy(jedisCommands);
    }

    @Bean
    @io.quarkus.arc.DefaultBean
    public MetadataDAO redisMetadataDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        return new RedisMetadataDAO(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Bean
    @io.quarkus.arc.DefaultBean
    public ExecutionDAO redisExecutionDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties,
            QueueDAO queueDAO) {
        return new RedisExecutionDAO(
                jedisProxy, objectMapper, conductorProperties, properties, queueDAO);
    }

    @Bean
    @io.quarkus.arc.DefaultBean
    public RateLimitingDAO redisRateLimitingDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        return new RedisRateLimitingDAO(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Bean
    @io.quarkus.arc.DefaultBean
    public ConcurrentExecutionLimitDAO redisConcurrentExecutionLimitDAO(ExecutionDAO executionDAO) {
        return (ConcurrentExecutionLimitDAO) executionDAO;
    }

    @Bean
    @io.quarkus.arc.DefaultBean
    public PollDataDAO redisPollDataDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        return new RedisPollDataDAO(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Bean
    @io.quarkus.arc.DefaultBean
    public EventHandlerDAO redisEventHandlerDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        return new RedisEventHandlerDAO(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Bean
    @io.quarkus.arc.DefaultBean
    public SchemaDAO redisSchemaDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        return new RedisSchemaDAO(jedisProxy, objectMapper, conductorProperties, properties);
    }
}
