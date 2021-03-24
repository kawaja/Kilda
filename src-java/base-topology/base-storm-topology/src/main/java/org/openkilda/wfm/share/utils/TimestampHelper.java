/* Copyright 2021 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.share.utils;

public final class TimestampHelper {
    private static final long TEN_TO_NINE = 1_000_000_000;

    private TimestampHelper() {}

    /**
     * Transform noviflow nanosecond precision time representation into regular.
     */
    public static long noviflowTimestamp(Long timestamp) {
        long seconds = (timestamp >> 32);
        long nanoseconds = (timestamp & 0xFFFFFFFFL);
        return seconds * TEN_TO_NINE + nanoseconds;
    }
}
