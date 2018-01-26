/* Copyright 2017 Telstra Open Source
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

package org.openkilda.pce.provider;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Statement;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.exceptions.NoSuchRecordException;
import org.neo4j.driver.v1.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class NeoDriver implements PathComputer {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(NeoDriver.class);

    /**
     * Clean database query.
     */
    private static final String CLEAN_FORMATTER_PATTERN = "MATCH (n) DETACH DELETE n";

    /**
     * Path query formatter pattern.
     */
    private static final String PATH_QUERY_FORMATTER_PATTERN =
            "MATCH (a:switch{name:{src_switch}}),(b:switch{name:{dst_switch}}), " +
                    "p = shortestPath((a)-[r:isl*..100]->(b)) " +
                    "where ALL(x in nodes(p) WHERE x.state = 'active') " +
                    "AND ALL(y in r WHERE y.available_bandwidth >= {bandwidth} AND y.status = 'active') " +
                    "RETURN p";

    /**
     * {@link Driver} instance.
     */
    private final Driver driver;

    /**
     * @param driver NEO4j driver(connect)
     */
    public NeoDriver(Driver driver) {
        this.driver = driver;
    }

    /**
     * Cleans database.
     */
    public void clean() {
        String query = CLEAN_FORMATTER_PATTERN;

        logger.info("Clean query: {}", query);

        Session session = driver.session();
        StatementResult result = session.run(query);
        session.close();

        logger.info("Switches deleted: {}", String.valueOf(result.summary().counters().nodesDeleted()));
        logger.info("Isl deleted: {}", String.valueOf(result.summary().counters().relationshipsDeleted()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow, Strategy strategy) throws UnroutablePathException {
        return getPath(flow.getSourceSwitch(), flow.getDestinationSwitch(), flow.getBandwidth(), strategy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(SwitchInfoData source, SwitchInfoData destination,
                                                             int bandwidth, Strategy strategy) throws UnroutablePathException {
        return getPath(source.getSwitchId(), destination.getSwitchId(), bandwidth, strategy);
    }

    private ImmutablePair<PathInfoData, PathInfoData> getPath(String srcSwitch, String dstSwitch, int bandwidth, Strategy strategy)
            throws UnroutablePathException {
        /*
         * TODO: implement strategy
         */


        long latency = 0L;
        List<PathNode> forwardNodes = new LinkedList<>();
        List<PathNode> reverseNodes = new LinkedList<>();

        if (!srcSwitch.equals(dstSwitch)) {
            Statement pathStatement = new Statement(PATH_QUERY_FORMATTER_PATTERN);
            Value value = Values.parameters("src_switch", srcSwitch, "dst_switch", dstSwitch, "bandwidth", bandwidth);

            Statement statement = pathStatement.withParameters(value);
            logger.debug("QUERY: {}", statement.toString());

            try (Session session = driver.session()) {
                StatementResult result = session.run(statement);

                try {
                    Record record = result.next();

                    LinkedList<Relationship> isls = new LinkedList<>();
                    record.get(0).asPath().relationships().forEach(isls::add);

                    int seqId = 0;
                    for (Relationship isl : isls) {
                        latency += isl.get("latency").asLong();

                        forwardNodes.add(new PathNode(isl.get("src_switch").asString(),
                                isl.get("src_port").asInt(), seqId, isl.get("latency").asLong()));
                        seqId++;

                        forwardNodes.add(new PathNode(isl.get("dst_switch").asString(),
                                isl.get("dst_port").asInt(), seqId, 0L));
                        seqId++;
                    }

                    seqId = 0;
                    Collections.reverse(isls);

                    for (Relationship isl : isls) {
                        reverseNodes.add(new PathNode(isl.get("dst_switch").asString(),
                                isl.get("dst_port").asInt(), seqId, isl.get("latency").asLong()));
                        seqId++;

                        reverseNodes.add(new PathNode(isl.get("src_switch").asString(),
                                isl.get("src_port").asInt(), seqId, 0L));
                        seqId++;
                    }
                } catch (NoSuchRecordException e) {
                    throw new UnroutablePathException(srcSwitch, dstSwitch, bandwidth);
                }
            }
        } else {
            logger.info("No path computation for one-switch flow");
        }

        return new ImmutablePair<>(new PathInfoData(latency, forwardNodes), new PathInfoData(latency, reverseNodes));
    }

}
