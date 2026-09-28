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
package com.netflix.conductor.server.quarkus;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class ConductorServerTest {

    @Test
    public void testHealthEndpoint() {
        given().when().get("/q/health").then().statusCode(200);
    }

    @Test
    public void testWorkflowMetadataEndpoint() {
        given().when().get("/api/metadata/workflow").then().statusCode(200);
    }

    @Test
    public void testCreateWorkflowDef() {
        String name = "test_import_workflow_" + System.currentTimeMillis();
        String workflowJson =
                String.format(
                        """
        {
          "name": "%s",
          "version": 1,
          "ownerEmail": "test@conductor.example",
          "tasks": [
            {
              "name": "simple_task",
              "taskReferenceName": "simple_task_ref",
              "type": "SIMPLE"
            }
          ]
        }
        """,
                        name);
        given().contentType("application/json")
                .body(workflowJson)
                .when()
                .post("/api/metadata/workflow")
                .then()
                .statusCode(204);
    }
}
