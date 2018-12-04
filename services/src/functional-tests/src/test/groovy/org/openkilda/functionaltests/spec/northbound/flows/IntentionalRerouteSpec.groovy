package org.openkilda.functionaltests.spec.northbound.flows

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.fixture.rule.CleanupSwitches
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative

@CleanupSwitches
@Narrative("Verify that on-demand reroute operations are performed accurately.")
class IntentionalRerouteSpec extends BaseSpecification {
    @Autowired
    TopologyDefinition topology
    @Autowired
    FlowHelper flowHelper
    @Autowired
    PathHelper pathHelper
    @Autowired
    NorthboundService northboundService
    @Autowired
    Database db
    @Autowired
    IslUtils islUtils

    def "Should not be able to reroute to a path with not enough bandwidth available"() {
        given: "A flow with alternate paths available"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> allPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.unique { it.sort() }.find { Switch src, Switch dst ->
            allPaths = db.getPaths(src.dpId, dst.dpId)*.path
            allPaths.size() > 1
        }
        assert srcSwitch
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 10000
        flowHelper.addFlow(flow)
        def currentPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        when: "Make the current path less preferable than alternatives"
        def alternativePaths = allPaths.findAll { it != currentPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }

        and: "Make all alternative paths to have not enough bandwidth to handle the flow"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def changedIsls = alternativePaths.collect { altPath ->
            def thinIsl = pathHelper.getInvolvedIsls(altPath).find {
                !currentIsls.contains(it) && !currentIsls.contains(islUtils.reverseIsl(it))
            }
            def newBw = flow.maximumBandwidth - 1
            db.updateLinkProperty(thinIsl, "max_bandwidth", newBw)
            db.updateLinkProperty(islUtils.reverseIsl(thinIsl), "max_bandwidth", newBw)
            db.updateLinkProperty(thinIsl, "available_bandwidth", newBw)
            db.updateLinkProperty(islUtils.reverseIsl(thinIsl), "available_bandwidth", newBw)
            thinIsl
        }

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = northboundService.rerouteFlow(flow.id)

        then: "The flow is NOT rerouted because of not enough bandwidth on alternative paths"
        int seqId = 0

        !rerouteResponse.rerouted
        rerouteResponse.path.path == currentPath
        rerouteResponse.path.path.each { assert it.seqId == seqId++ }

        PathHelper.convert(northboundService.getFlowPath(flow.id)) == currentPath
        northbound.getFlowStatus(flow.id).status == FlowState.UP

        and: "Remove the flow, restore the bandwidth on ISLs, reset costs"
        flowHelper.deleteFlow(flow.id)
        changedIsls.each {
            db.revertIslBandwidth(it)
            db.revertIslBandwidth(islUtils.reverseIsl(it))
        }
    }

    def "Should be able to reroute to a better path if it has enough bandwidth"() {
        given: "A flow with alternate paths available"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> allPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.unique { it.sort() }.find { Switch src, Switch dst ->
            allPaths = db.getPaths(src.dpId, dst.dpId)*.path
            allPaths.size() > 1
        }
        assert srcSwitch
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 10000
        flowHelper.addFlow(flow)
        def currentPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        when: "Make one of the alternative paths to be the most preferable among all others"
        def preferableAltPath = allPaths.find { it != currentPath }
        allPaths.findAll { it != preferableAltPath }.each { pathHelper.makePathMorePreferable(preferableAltPath, it) }

        and: "Make the future path to have exact bandwidth to handle the flow"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def thinIsl = pathHelper.getInvolvedIsls(preferableAltPath).find {
            !currentIsls.contains(it) && !currentIsls.contains(islUtils.reverseIsl(it))
        }
        db.updateLinkProperty(thinIsl, "max_bandwidth", flow.maximumBandwidth)
        db.updateLinkProperty(islUtils.reverseIsl(thinIsl), "max_bandwidth", flow.maximumBandwidth)
        db.updateLinkProperty(thinIsl, "available_bandwidth", flow.maximumBandwidth)
        db.updateLinkProperty(islUtils.reverseIsl(thinIsl), "available_bandwidth", flow.maximumBandwidth)

        and: "Init a reroute of the flow"
        def rerouteResponse = northboundService.rerouteFlow(flow.id)

        then: "The flow is successfully rerouted and goes through the preferable path"
        def newPath = PathHelper.convert(northboundService.getFlowPath(flow.id))
        int seqId = 0

        rerouteResponse.rerouted
        rerouteResponse.path.path == newPath
        rerouteResponse.path.path.each { assert it.seqId == seqId++ }

        newPath == preferableAltPath
        pathHelper.getInvolvedIsls(newPath).contains(thinIsl)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "'Thin' ISL has 0 available bandwidth left"
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(thinIsl).get().availableBandwidth == 0 }

        and: "Remove the flow, restore bandwidths on ISLs, reset costs"
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        flowHelper.deleteFlow(flow.id)
        [thinIsl, islUtils.reverseIsl(thinIsl)].each { db.revertIslBandwidth(it) }
    }

    def cleanup() {
        northboundService.deleteLinkProps(northboundService.getAllLinkProps())
        db.resetCosts()
    }
}