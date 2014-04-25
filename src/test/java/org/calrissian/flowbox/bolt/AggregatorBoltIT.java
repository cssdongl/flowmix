package org.calrissian.flowbox.bolt;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.generated.StormTopology;
import org.calrissian.flowbox.FlowboxTopology;
import org.calrissian.flowbox.model.Event;
import org.calrissian.flowbox.model.Flow;
import org.calrissian.flowbox.model.Policy;
import org.calrissian.flowbox.model.Tuple;
import org.calrissian.flowbox.model.builder.FlowBuilder;
import org.calrissian.flowbox.spout.MockEventGeneratorSpout;
import org.calrissian.flowbox.spout.MockFlowLoaderSpout;
import org.calrissian.flowbox.support.Aggregator;
import org.calrissian.flowbox.support.WindowItem;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

public class AggregatorBoltIT {

    private StormTopology buildTopology(Flow flow) {
        StormTopology topology = new FlowboxTopology().buildTopology(
                new MockFlowLoaderSpout(singletonList(flow), 60000),
                new MockEventGeneratorSpout(10),
                new MockSinkBolt(),
                6);

        return topology;
    }

    @Test
    public void test() {

        Flow flow = new FlowBuilder()
            .id("myflow")
            .flowDefs()
                .stream("stream1")
                    .partition().field("key3").end()
                    .aggregate().aggregator(TestAggregator.class).trigger(Policy.TIME, 5).evict(Policy.TIME, 10).end()
                .endStream()
            .endDefs()
        .createFlow();

        StormTopology topology = buildTopology(flow);
        Config conf = new Config();
        conf.setNumWorkers(20);

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology("test", conf, topology);

        try {
            Thread.sleep(25000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(4, MockSinkBolt.getEvents().size());
    }

    public static final class TestAggregator implements Aggregator {

        private long count;

        @Override
        public void added(WindowItem item) {
            count++;
        }

        @Override
        public void evicted(WindowItem item) {
            count--;

        }

        @Override
        public List<Event> aggregate() {
            Event toReturn = new Event(UUID.randomUUID().toString(), currentTimeMillis());
            toReturn.put(new Tuple("count", count));

            count = 0;
            return singletonList(toReturn);
        }

    }
}
