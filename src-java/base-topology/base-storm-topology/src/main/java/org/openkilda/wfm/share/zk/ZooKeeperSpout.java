/* Copyright 2020 Telstra Open Source
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


package org.openkilda.wfm.share.zk;

import org.openkilda.bluegreen.LifeCycleObserver;
import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.bluegreen.Signal;
import org.openkilda.bluegreen.ZkClient;
import org.openkilda.bluegreen.ZkWatchDog;
import org.openkilda.config.ZookeeperConfig;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.apache.zookeeper.KeeperException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public class ZooKeeperSpout extends BaseRichSpout implements LifeCycleObserver {
    public static final String SPOUT_ID = "zookeeper.spout";
    public static final String FIELD_ID_LIFECYCLE_EVENT = "lifecycle.event";

    public static final String FIELD_ID_CONTEXT = AbstractBolt.FIELD_ID_CONTEXT;
    private final String id;
    private final String serviceName;
    private final String connectionString;
    private final long reconnectDelayMs;
    private Instant zooKeeperConnectionTimestamp = Instant.MIN;
    private transient Queue<Signal> signals;
    private transient ZkWatchDog watchDog;
    private transient SpoutOutputCollector collector;
    private long messageId = 0;


    public ZooKeeperSpout(String id, String serviceName, ZookeeperConfig zookeeperConfig) {
        this.id = id;
        this.serviceName = serviceName;
        this.connectionString = zookeeperConfig.getConnectString();
        this.reconnectDelayMs = zookeeperConfig.getReconnectDelay();
    }

    @Override
    public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {
        this.collector = collector;
        this.signals = new ConcurrentLinkedQueue<>();
        this.watchDog = ZkWatchDog.builder().id(id).serviceName(serviceName)
                .connectionString(connectionString)
                .connectionRefreshInterval(ZkClient.DEFAULT_CONNECTION_REFRESH_INTERVAL)
                .reconnectDelayMs(reconnectDelayMs)
                .build();
        watchDog.subscribe(this);
        watchDog.initAndWaitConnection();
        forceReadSignal();
    }

    protected boolean isZooKeeperConnectTimeoutPassed() {
        return zooKeeperConnectionTimestamp.plus(10, ChronoUnit.SECONDS)
                .isBefore(Instant.now());
    }

    @Override
    public void nextTuple() {
        Signal signal = signals.poll();
        if (signal != null) {
            LifecycleEvent event = LifecycleEvent.builder()
                    .signal(signal)
                    .uuid(UUID.randomUUID())
                    .messageId(messageId++).build();
            log.info("Component {} with id {} received signal {} from zookeeper. Sending event {}",
                    serviceName, id, signal, event);
            collector.emit(new Values(event, new CommandContext()), messageId);
        } else {
            org.apache.storm.utils.Utils.sleep(1L);
        }
        if (!watchDog.isConnectedAndValidated()) {
            if (isZooKeeperConnectTimeoutPassed()) {
                log.info("Service {} with run_id {} tries to reconnect to ZooKeeper {}",
                        serviceName, id, connectionString);
                watchDog.safeRefreshConnection();
                zooKeeperConnectionTimestamp = Instant.now();
            }
        }
    }

    @Override
    public void ack(Object msgId) {
        super.ack(msgId);
    }


    @Override
    public void fail(Object msgId) {
        log.error("Failed to process message {}", msgId);
        super.fail(msgId);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(FIELD_ID_LIFECYCLE_EVENT, FIELD_ID_CONTEXT));

    }

    @Override
    public void handle(Signal signal) {
        log.info("Received signal {}", signal);
        signals.add(signal);
    }

    private void forceReadSignal() {
        Signal signal = null;
        try {
            signal = watchDog.getSignalSync();
        } catch (KeeperException | InterruptedException e) {
            log.error(String.format("Couldn't get signal for component %s and id %s. Error: %s",
                    serviceName, id, e.getMessage()), e);
        }

        if (signal == null) {
            log.error("Couldn't get signal for component {} and id {}. Signal is null.", serviceName, id);
        } else {
            handle(signal);
        }
    }
}
