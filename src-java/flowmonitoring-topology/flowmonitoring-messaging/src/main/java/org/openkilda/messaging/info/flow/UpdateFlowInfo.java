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

package org.openkilda.messaging.info.flow;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.FlowPathDto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Represents update flow info.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateFlowInfo extends InfoData {
    private static final long serialVersionUID = 1L;

    @JsonProperty("flow_id")
    protected String flowId;

    @JsonProperty("flow_path")
    protected FlowPathDto flowPath;

    @JsonProperty("max_latency")
    protected Long maxLatency;

    @JsonProperty("max_latency_tier_2")
    protected Long maxLatencyTier2;

    @JsonCreator
    public UpdateFlowInfo(@JsonProperty("flow_id") String flowId,
                          @JsonProperty("flow_path") FlowPathDto flowPath,
                          @JsonProperty("max_latency") Long maxLatency,
                          @JsonProperty("max_latency_tier_2") Long maxLatencyTier2) {
        this.flowId = flowId;
        this.flowPath = flowPath;
        this.maxLatency = maxLatency;
        this.maxLatencyTier2 = maxLatencyTier2;
    }
}
