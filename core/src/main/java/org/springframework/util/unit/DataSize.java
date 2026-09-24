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

import java.io.Serializable;

public final class DataSize implements Comparable<DataSize>, Serializable {
    private static final long BYTES_PER_KB = 1024L;
    private static final long BYTES_PER_MB = BYTES_PER_KB * 1024L;
    private static final long BYTES_PER_GB = BYTES_PER_MB * 1024L;
    private static final long BYTES_PER_TB = BYTES_PER_GB * 1024L;

    private final long bytes;

    private DataSize(long bytes) {
        this.bytes = bytes;
    }

    public static DataSize ofBytes(long bytes) {
        return new DataSize(bytes);
    }

    public static DataSize ofKilobytes(long kilobytes) {
        return new DataSize(Math.multiplyExact(kilobytes, BYTES_PER_KB));
    }

    public static DataSize ofMegabytes(long megabytes) {
        return new DataSize(Math.multiplyExact(megabytes, BYTES_PER_MB));
    }

    public static DataSize ofGigabytes(long gigabytes) {
        return new DataSize(Math.multiplyExact(gigabytes, BYTES_PER_GB));
    }

    public static DataSize ofTerabytes(long terabytes) {
        return new DataSize(Math.multiplyExact(terabytes, BYTES_PER_TB));
    }

    public static DataSize of(long amount, DataUnit unit) {
        return new DataSize(Math.multiplyExact(amount, unit.size().toBytes()));
    }

    public static DataSize parse(CharSequence text) {
        String s = text.toString().trim().toUpperCase();
        if (s.endsWith("KB")) {
            return ofKilobytes(Long.parseLong(s.substring(0, s.length() - 2).trim()));
        } else if (s.endsWith("MB")) {
            return ofMegabytes(Long.parseLong(s.substring(0, s.length() - 2).trim()));
        } else if (s.endsWith("GB")) {
            return ofGigabytes(Long.parseLong(s.substring(0, s.length() - 2).trim()));
        } else if (s.endsWith("B")) {
            return ofBytes(Long.parseLong(s.substring(0, s.length() - 1).trim()));
        }
        return ofBytes(Long.parseLong(s));
    }

    public long toBytes() {
        return this.bytes;
    }

    public long toKilobytes() {
        return this.bytes / BYTES_PER_KB;
    }

    public long toMegabytes() {
        return this.bytes / BYTES_PER_MB;
    }

    public long toGigabytes() {
        return this.bytes / BYTES_PER_GB;
    }

    @Override
    public int compareTo(DataSize other) {
        return Long.compare(this.bytes, other.bytes);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DataSize)) return false;
        return this.bytes == ((DataSize) other).bytes;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(this.bytes);
    }

    @Override
    public String toString() {
        return String.format("%dB", this.bytes);
    }
}
