package samzaapp;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import joptsimple.OptionSet;
import org.apache.samza.application.StreamApplication;
import org.apache.samza.application.descriptors.StreamApplicationDescriptor;
import org.apache.samza.config.Config;
import org.apache.samza.operators.KV;
import org.apache.samza.operators.MessageStream;
import org.apache.samza.operators.OutputStream;
import org.apache.samza.operators.windows.Windows;
import org.apache.samza.runtime.LocalApplicationRunner;
import org.apache.samza.serializers.IntegerSerde;
import org.apache.samza.serializers.KVSerde;
import org.apache.samza.serializers.StringSerde;
import org.apache.samza.system.kafka.descriptors.KafkaInputDescriptor;
import org.apache.samza.system.kafka.descriptors.KafkaOutputDescriptor;
import org.apache.samza.system.kafka.descriptors.KafkaSystemDescriptor;
import org.apache.samza.util.CommandLine;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class WordCount implements StreamApplication {

  private static final String KAFKA_SYSTEM_NAME = "kafka";
  private static final List<String> KAFKA_CONSUMER_ZK_CONNECT = ImmutableList.of("localhost:2181");
  private static final List<String> KAFKA_PRODUCER_BOOTSTRAP_SERVERS = ImmutableList.of("localhost:9092");
  private static final Map<String, String> KAFKA_DEFAULT_STREAM_CONFIGS = ImmutableMap.of("replication.factor", "1");

  private static final String INPUT_STREAM_ID = "EVENT_STREAM_INPUT";
  private static final String OUTPUT_STREAM_ID = "EVENT_STREAM_OUTPUT";

  public static void main(String[] args) {
    CommandLine cmdLine = new CommandLine();
    OptionSet options = cmdLine.parser().parse(args);
    Config config = cmdLine.loadConfig(options);
    LocalApplicationRunner runner = new LocalApplicationRunner(new WordCount(), config);
    runner.run();
    runner.waitForFinish();
  }

  @Override
  public void describe(StreamApplicationDescriptor appDescriptor) {

    // Create a KafkaSystemDescriptor providing properties of the cluster
    KafkaSystemDescriptor kafkaSystemDescriptor = new KafkaSystemDescriptor(KAFKA_SYSTEM_NAME)
            .withConsumerZkConnect(KAFKA_CONSUMER_ZK_CONNECT)
            .withProducerBootstrapServers(KAFKA_PRODUCER_BOOTSTRAP_SERVERS)
            .withDefaultStreamConfigs(KAFKA_DEFAULT_STREAM_CONFIGS);


    // For each input or output stream, create a KafkaInput/Output descriptor
    KafkaInputDescriptor<KV<String, String>> inputDescriptor =
            kafkaSystemDescriptor.getInputDescriptor(INPUT_STREAM_ID,
                    KVSerde.of(new StringSerde(), new StringSerde()));
    KafkaOutputDescriptor<KV<String, String>> outputDescriptor =
            kafkaSystemDescriptor.getOutputDescriptor(OUTPUT_STREAM_ID,
                    KVSerde.of(new StringSerde(), new StringSerde()));

    // Obtain a handle to a MessageStream that you can chain operations on
    MessageStream<KV<String, String>> lines = appDescriptor.getInputStream(inputDescriptor);
    OutputStream<KV<String, String>> counts = appDescriptor.getOutputStream(outputDescriptor);


    //Operations on Input Stream
    lines
            .map(kv -> kv.value)
            .flatMap(s -> Arrays.asList(s.split("\\W+")))
            .window(Windows.keyedSessionWindow(
                    w -> w, Duration.ofSeconds(5), () -> 0, (m, prevCount) -> prevCount + 1,
                    new StringSerde(), new IntegerSerde()), "count")
            .map(windowPane ->
                    KV.of(windowPane.getKey().getKey(),
                            windowPane.getKey().getKey() + ": " + windowPane.getMessage().toString()))
            .sendTo(counts);
  }
}
