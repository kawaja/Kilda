/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.GetLinksRequest;
import org.openkilda.messaging.nbtopology.request.LinkPropsGet;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.mappers.IslMapper;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LinkOperationsBolt extends PersistenceOperationsBolt {
    public LinkOperationsBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    @SuppressWarnings("unchecked")
    List<InfoData> processRequest(Tuple tuple, BaseRequest request) {
        List<? extends InfoData> result = null;
        if (request instanceof GetLinksRequest) {
            result = getAllLinks();
        } else if (request instanceof LinkPropsGet) {
            result = getLinkProps((LinkPropsGet) request);
        } else {
            unhandledInput(tuple);
        }

        return (List<InfoData>) result;
    }

    private List<IslInfoData> getAllLinks() {
        return repositoryFactory.createIslRepository().findAll().stream()
                .map(IslMapper.INSTANCE::map)
                .collect(Collectors.toList());
    }

    private List<LinkPropsData> getLinkProps(LinkPropsGet request) {
        // TODO: should be re-implemented in the scope of TE refactoring.
        return Collections.emptyList();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("response", "correlationId"));
    }
}
