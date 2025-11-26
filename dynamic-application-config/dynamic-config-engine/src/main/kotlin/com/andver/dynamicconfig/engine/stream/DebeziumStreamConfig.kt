package com.andver.dynamicconfig.engine.stream

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private const val OPERATION = "op"
private const val CREATE = "c"
private const val UPDATE = "u"
private const val AFTER = "after"
private const val PAYLOAD = "payload"
private const val KEY = "key"
private const val VALUE = "value"

@Configuration
class DebeziumStreamConfig(
  private val kStreamBuilder: StreamsBuilder,
  @Value("\${dynamic-config.debezium-topic}") private val debeziumTopic: String,
  @Value("\${dynamic-config.topic}") private val resultTopic: String,
) {
  private val mapper = jacksonObjectMapper()

  private val logger = LoggerFactory.getLogger(DebeziumStreamConfig::class.java)

  @Bean
  fun debeziumStream(): KStream<String, String> {
    return kStreamBuilder.stream(debeziumTopic, Consumed.with(STRING_SERDE, STRING_SERDE))
      .mapValues { json -> deserializeJson(json) }
      .mapValues { root -> root[PAYLOAD] as Map<*, *> }
      .filter { _, payload -> payload[OPERATION].toString() in SUPPORTED_OPERATIONS }
      .mapValues { payload -> payload[AFTER] as Map<*, *> }
      .map { _, after ->
        KeyValue(after[KEY].toString(), after[VALUE].toString()).also {
          logger.info("Push config change to [$debeziumTopic] -> $it")
        }
      }
      .also {
        it.to(resultTopic, Produced.with(Serdes.String(), Serdes.String()))
      }
  }

  private fun deserializeJson(json: String): Map<String, Any?> {
    return mapper.readValue<Map<String, Any?>>(json)
  }

  companion object {
    private val STRING_SERDE = Serdes.String()
    private val SUPPORTED_OPERATIONS = listOf(CREATE, UPDATE)
  }
}