/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class OnReceivedRemoveOrRevertResponseAction extends
        HistoryRecordingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {
    private final int speakerCommandRetriesLimit;

    public OnReceivedRemoveOrRevertResponseAction(int speakerCommandRetriesLimit) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        SpeakerFlowSegmentResponse response = context.getSpeakerFlowResponse();
        UUID commandId = response.getCommandId();
        FlowSegmentRequestFactory removeCommand = stateMachine.getRemoveCommands().get(commandId);
        FlowSegmentRequestFactory installCommand = stateMachine.getInstallCommand(commandId);
        if (!stateMachine.getPendingCommands().containsKey(commandId)
                || (removeCommand == null && installCommand == null)) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        MeterRegistryHolder.getRegistry().ifPresent(registry -> {
            if (response.getRequestCreateTime() > 0) {
                long roundtrip = Duration.between(Instant.ofEpochMilli(response.getRequestCreateTime()),
                        Instant.now()).toNanos();
                if (roundtrip > 0) {
                    registry.timer("fsm.remove_command.roundtrip")
                            .record(roundtrip, TimeUnit.NANOSECONDS);
                }
            }

            if (response.getExecutionTime() > 0) {
                registry.timer("fsm.remove_command.floodlight_execution")
                        .record(response.getExecutionTime(), TimeUnit.NANOSECONDS);
            }
        });

        if (response.isSuccess()) {
            stateMachine.removePendingCommand(commandId);

            if (removeCommand != null) {
                stateMachine.saveActionToHistory("Rule was deleted",
                        format("The rule was removed: switch %s, cookie %s", response.getSwitchId(),
                                removeCommand.getCookie()));
            } else {
                stateMachine.saveActionToHistory("Rule was re-installed (reverted)",
                        format("The rule was installed: switch %s, cookie %s",
                                response.getSwitchId(), installCommand.getCookie()));
            }
        } else {
            FlowErrorResponse errorResponse = (FlowErrorResponse) response;

            int attempt = stateMachine.doRetryForCommand(commandId);
            if (attempt <= speakerCommandRetriesLimit) {
                if (removeCommand != null) {
                    stateMachine.saveErrorToHistory("Failed to remove rule", format(
                            "Failed to remove the rule: commandId %s, switch %s, cookie %s. Error %s. "
                                    + "Retrying (attempt %d)",
                            commandId, errorResponse.getSwitchId(), removeCommand.getCookie(), errorResponse, attempt));

                    stateMachine.getCarrier().sendSpeakerRequest(removeCommand.makeRemoveRequest(commandId));
                } else {
                    stateMachine.saveErrorToHistory("Failed to re-install (revert) rule", format(
                            "Failed to install the rule: commandId %s, switch %s, cookie %s. Error %s. "
                                    + "Retrying (attempt %d)", commandId, errorResponse.getSwitchId(),
                            installCommand.getCookie(), errorResponse, attempt));

                    stateMachine.getCarrier().sendSpeakerRequest(installCommand.makeInstallRequest(commandId));
                }
            } else {
                stateMachine.removePendingCommand(commandId);

                if (removeCommand != null) {
                    stateMachine.saveErrorToHistory("Failed to remove rule", format(
                            "Failed to remove the rule: commandId %s, switch %s, cookie %s. Error: %s",
                            commandId, errorResponse.getSwitchId(), removeCommand.getCookie(), errorResponse));
                } else {
                    stateMachine.saveErrorToHistory("Failed to re-install rule", format(
                            "Failed to install the rule: commandId %s, switch %s, cookie %s. Error: %s",
                            commandId, errorResponse.getSwitchId(), installCommand.getCookie(), errorResponse));
                }
                stateMachine.addFailedCommand(commandId, errorResponse);
            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedCommands().isEmpty()) {
                log.debug("Received responses for all pending remove / re-install commands of the flow {}",
                        stateMachine.getFlowId());
                stateMachine.fire(Event.RULES_REMOVED);
            } else {
                String errorMessage = format("Received error response(s) for %d remove / re-install commands",
                        stateMachine.getFailedCommands().size());
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}
