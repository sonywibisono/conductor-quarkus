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
package com.netflix.conductor.postgres.util;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight native retry template for PostgreSQL operations. Retries transactions when deadlock
 * (40P01) or serialization failure (40001) errors occur, eliminating the dependency on Spring Retry
 * in Quarkus environments.
 */
public class PostgresRetryTemplate {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresRetryTemplate.class);

    private static final String ER_LOCK_DEADLOCK = "40P01";
    private static final String ER_SERIALIZATION_FAILURE = "40001";

    private final int maxAttempts;

    public PostgresRetryTemplate() {
        this(3);
    }

    public PostgresRetryTemplate(int maxAttempts) {
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : 3;
    }

    @FunctionalInterface
    public interface RetryCallback<T> {
        T doWithRetry() throws Exception;
    }

    /**
     * Executes the given callback, retrying if a PostgreSQL deadlock or serialization failure
     * occurs.
     *
     * @param callback the operation to execute
     * @param <T> the return type
     * @return the result of the callback
     * @throws Exception if max retries are exceeded or a non-retryable exception occurs
     */
    public <T> T execute(RetryCallback<T> callback) throws Exception {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return callback.doWithRetry();
            } catch (Throwable th) {
                if (attempts < maxAttempts && isDeadLockError(th)) {
                    LOGGER.warn(
                            "PostgreSQL deadlock or serialization failure detected (attempt {} of {}), retrying...",
                            attempts,
                            maxAttempts,
                            th);
                    continue;
                }
                if (th instanceof Exception) {
                    throw (Exception) th;
                }
                throw new RuntimeException(th);
            }
        }
    }

    public static boolean isDeadLockError(Throwable throwable) {
        SQLException sqlException = findCauseSQLException(throwable);
        if (sqlException == null) {
            return false;
        }
        return ER_LOCK_DEADLOCK.equals(sqlException.getSQLState())
                || ER_SERIALIZATION_FAILURE.equals(sqlException.getSQLState());
    }

    public static SQLException findCauseSQLException(Throwable throwable) {
        Throwable causeException = throwable;
        while (null != causeException && !(causeException instanceof SQLException)) {
            causeException = causeException.getCause();
        }
        return (SQLException) causeException;
    }
}
