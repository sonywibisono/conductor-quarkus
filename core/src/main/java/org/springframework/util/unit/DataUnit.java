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
package org.springframework.util.unit;

public enum DataUnit {
    BYTES("B", DataSize.ofBytes(1L)),
    KILOBYTES("KB", DataSize.ofKilobytes(1L)),
    MEGABYTES("MB", DataSize.ofMegabytes(1L)),
    GIGABYTES("GB", DataSize.ofGigabytes(1L)),
    TERABYTES("TB", DataSize.ofTerabytes(1L));

    private final String suffix;
    private final DataSize size;

    DataUnit(String suffix, DataSize size) {
        this.suffix = suffix;
        this.size = size;
    }

    public DataSize size() {
        return this.size;
    }
}
