package com.andver.dhm

import com.andver.dhm.admin.DistributedMapAdminController
import com.andver.dhm.cleanup.TombstoneCleaner
import com.andver.dhm.envelope.MapEventCodec
import com.andver.dhm.kafka.MapEventApplier
import com.andver.dhm.kafka.MapEventConsumerLoop
import com.andver.dhm.kafka.MapEventProducer
import com.andver.dhm.kafka.TopicEnsurer
import com.andver.dhm.kafka.TopicResolver
import com.andver.dhm.metrics.DistributedMapMetrics
import com.andver.dhm.properties.DistributedMapProperties
import com.andver.dhm.readiness.DistributedMapHealthIndicator
import com.andver.dhm.readiness.MapReadinessTracker
import com.andver.dhm.runtime.DistributedMapImpl
import com.andver.dhm.runtime.LocalState
import com.andver.dhm.runtime.MapRuntimeRegistry
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.web.reactive.function.server.RouterFunction
import java.time.Clock
import java.util.UUID

@AutoConfiguration
@EnableConfigurationProperties(DistributedMapProperties::class)
@ConditionalOnProperty(
  prefix = "distributed.map",
  name = ["enabled"],
  havingValue = "true",
  matchIfMissing = true,
)
class DistributedMapAutoConfiguration {

  private val log = LoggerFactory.getLogger(DistributedMapAutoConfiguration::class.java)

  @Bean
  @ConditionalOnMissingBean(name = ["distributedMapClock"])
  fun distributedMapClock(): Clock = Clock.systemUTC()

  @Bean
  fun distributedMapObjectMapper(): ObjectMapper = ObjectMapper()
    .registerModule(KotlinModule.Builder().build())
    .registerModule(JavaTimeModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  @Bean
  fun mapEventCodec(distributedMapObjectMapper: ObjectMapper): MapEventCodec =
    MapEventCodec(distributedMapObjectMapper)

  @Bean
  fun topicResolver(properties: DistributedMapProperties): TopicResolver = TopicResolver(properties)

  @Bean(destroyMethod = "close")
  fun distributedMapAdminClient(kafkaProperties: KafkaProperties): AdminClient {
    val props = HashMap<String, Any>()
    props.putAll(kafkaProperties.buildAdminProperties())
    props.putIfAbsent(
      AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
      kafkaProperties.bootstrapServers ?: listOf("localhost:9092"),
    )
    return AdminClient.create(props)
  }

  @Bean
  fun topicEnsurer(
    adminClient: AdminClient,
    properties: DistributedMapProperties,
    topicResolver: TopicResolver,
  ): TopicEnsurer {
    val ensurer = TopicEnsurer(adminClient, properties, topicResolver)
    ensurer.ensureAllTopics()
    return ensurer
  }

  @Bean
  fun distributedMapProducerFactory(kafkaProperties: KafkaProperties): ProducerFactory<String, String> {
    val props = HashMap<String, Any>()
    props.putAll(kafkaProperties.buildProducerProperties())
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all")
    return DefaultKafkaProducerFactory(props)
  }

  @Bean
  fun distributedMapKafkaTemplate(
    distributedMapProducerFactory: ProducerFactory<String, String>,
  ): KafkaTemplate<String, String> = KafkaTemplate(distributedMapProducerFactory)

  @Bean
  fun mapEventProducer(
    distributedMapKafkaTemplate: KafkaTemplate<String, String>,
    mapEventCodec: MapEventCodec,
    topicResolver: TopicResolver,
  ): MapEventProducer = MapEventProducer(distributedMapKafkaTemplate, mapEventCodec, topicResolver)

  @Bean
  fun distributedMapMetrics(meterRegistryProvider: ObjectProvider<MeterRegistry>): DistributedMapMetrics {
    val registry = meterRegistryProvider.ifAvailable ?: CompositeMeterRegistry()
    return DistributedMapMetrics(registry)
  }

  @Bean
  fun mapReadinessTracker(properties: DistributedMapProperties): MapReadinessTracker =
    MapReadinessTracker(properties.maps.keys)

  @Bean
  fun distributedMapHealthIndicator(tracker: MapReadinessTracker): DistributedMapHealthIndicator =
    DistributedMapHealthIndicator(tracker)

  @Bean
  fun mapRuntimeRegistry(
    properties: DistributedMapProperties,
    distributedMapClock: Clock,
    mapEventCodec: MapEventCodec,
    mapEventProducer: MapEventProducer,
    distributedMapMetrics: DistributedMapMetrics,
  ): MapRuntimeRegistry {
    val nodeId = properties.nodeId ?: "node-${UUID.randomUUID()}"
    val maps = properties.maps.mapValues { (name, def) ->
      val valueType = resolveValueType(def.valueType)
      buildMap(
        name = name,
        valueType = valueType,
        nodeId = nodeId,
        clock = distributedMapClock,
        codec = mapEventCodec,
        producer = mapEventProducer,
        metrics = distributedMapMetrics,
      )
    }
    val registry = MapRuntimeRegistry(maps)
    distributedMapMetrics.bindGauges(registry)
    log.info("Configured {} distributed map(s): {}", maps.size, maps.keys)
    return registry
  }

  @Bean
  fun mapEventApplier(
    mapEventCodec: MapEventCodec,
    mapRuntimeRegistry: MapRuntimeRegistry,
    topicResolver: TopicResolver,
    distributedMapMetrics: DistributedMapMetrics,
  ): MapEventApplier = MapEventApplier(
    codec = mapEventCodec,
    runtime = mapRuntimeRegistry,
    topicResolver = topicResolver,
    metrics = distributedMapMetrics,
  )

  @Bean(initMethod = "start", destroyMethod = "stop")
  fun mapEventConsumerLoop(
    kafkaProperties: KafkaProperties,
    properties: DistributedMapProperties,
    topicResolver: TopicResolver,
    mapEventApplier: MapEventApplier,
    mapReadinessTracker: MapReadinessTracker,
    @Suppress("UNUSED_PARAMETER") topicEnsurer: TopicEnsurer,
  ): MapEventConsumerLoop = MapEventConsumerLoop(
    kafkaProperties = kafkaProperties,
    properties = properties,
    topicResolver = topicResolver,
    applier = mapEventApplier,
    readiness = mapReadinessTracker,
  )

  @Bean(initMethod = "start", destroyMethod = "stop")
  fun tombstoneCleaner(
    properties: DistributedMapProperties,
    mapRuntimeRegistry: MapRuntimeRegistry,
    mapEventProducer: MapEventProducer,
    distributedMapMetrics: DistributedMapMetrics,
    distributedMapClock: Clock,
  ): TombstoneCleaner = TombstoneCleaner(
    properties = properties,
    runtime = mapRuntimeRegistry,
    producer = mapEventProducer,
    metrics = distributedMapMetrics,
    clock = distributedMapClock,
  )

  @Configuration
  @ConditionalOnClass(RouterFunction::class)
  @ConditionalOnProperty(
    prefix = "distributed.map.admin",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
  )
  class AdminEndpointConfiguration {

    @Bean
    fun distributedMapAdminController(
      mapRuntimeRegistry: MapRuntimeRegistry,
      mapReadinessTracker: MapReadinessTracker,
      properties: DistributedMapProperties,
    ): DistributedMapAdminController =
      DistributedMapAdminController(mapRuntimeRegistry, mapReadinessTracker, properties)
  }

  private fun <V : Any> buildMap(
    name: String,
    valueType: Class<V>,
    nodeId: String,
    clock: Clock,
    codec: MapEventCodec,
    producer: MapEventProducer,
    metrics: DistributedMapMetrics,
  ): DistributedMapImpl<V> {
    val state = LocalState(name, valueType, codec)
    return DistributedMapImpl(
      name = name,
      valueType = valueType,
      nodeId = nodeId,
      clock = clock,
      codec = codec,
      producer = producer,
      metrics = metrics,
      localState = state,
    )
  }

  @Suppress("UNCHECKED_CAST")
  private fun resolveValueType(fqcn: String): Class<Any> {
    val classLoader = Thread.currentThread().contextClassLoader
      ?: DistributedMapAutoConfiguration::class.java.classLoader
    return Class.forName(fqcn, true, classLoader) as Class<Any>
  }
}
