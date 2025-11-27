package com.andver.dynamicconfig.engine.stream

import com.andver.dynamicconfig.engine.properties.DynamicConfigEngineProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KTable
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.TimeWindows
import org.apache.kafka.streams.kstream.Windowed
import org.apache.kafka.streams.kstream.WindowedSerdes
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@ConditionalOnProperty(
  prefix = "dynamic-config",
  name = ["analyze-consistency-enabled"],
  havingValue = "true",
  matchIfMissing = false
)
class ConsistencyAnalyzerConfig(
  private val kStreamBuilder: StreamsBuilder,
  private val props: DynamicConfigEngineProperties,
) {
  private val mapper = jacksonObjectMapper()

  @Bean
  fun consistencyAnalyzerStream(): KStream<Windowed<String?>?, String?>? {
    val reference: KTable<String, String> =
      kStreamBuilder.table(props.topic, Consumed.with(Serdes.String(), Serdes.String()))

    return kStreamBuilder.stream(props.snapshotTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .flatMap { _, json ->
        val node = mapper.readTree(json)
        val appName = node["appName"].asText()
        val configs = node["configs"]
        configs.fields().asSequence().map {
          val configKey = it.key
          val configValue = it.value.asText()
          KeyValue(it.key, """{"appName":"$appName","configKey":"$configKey","value":"$configValue"}""")
        }.toList()
      }.join(reference) { nodeValue, refValue ->
        val node = mapper.readTree(nodeValue)
        val appName = node["appName"].asText()
        val configKey = node["configKey"].asText()
        val value = node["value"].asText()

        if (value != refValue) {
          """{"appName":"$appName","configKey":"$configKey","expected":"$refValue","actual":"$value"}"""
        } else null
      }.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
      .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(props.windowSeconds)))
      .aggregate(
        { mutableListOf() },
        { _, value: String?, list: MutableList<String> -> list.apply { if (value != null) add(value) } },
        Materialized.with(Serdes.String(), Serdes.ListSerde())
      )
      .toStream()
      .filter { _, inconsistencies -> inconsistencies.isNotEmpty() }
      .mapValues { inconsistencies -> inconsistencies.joinToString(",") }
      .also {
        it.to(
          props.alertsTopic,
          Produced.with(WindowedSerdes.timeWindowedSerdeFrom(String::class.java), Serdes.String())
        )
      }
  }
}
