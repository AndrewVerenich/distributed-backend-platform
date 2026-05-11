package com.andver.dhm.kafka

import com.andver.dhm.properties.DistributedMapProperties
import com.andver.dhm.readiness.MapReadinessTracker
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.SmartLifecycle
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Long-running Kafka consumer loop with two phases:
 *
 *  1. **Bootstrap restore**: assigns every map topic, seeks to beginning and polls until the
 *     current end-offset is reached on every partition. Only then [MapReadinessTracker] flips
 *     the map to READY. The application is allowed to start, but the readiness probe stays DOWN
 *     until restore completes — this matches the "readiness barrier" described in the plan.
 *
 *  2. **Steady state**: keeps polling and applying events from the live tail of the topic.
 *
 * We use a fresh, random consumer group per process instance so every node receives every event
 * (pub-sub style consumption on top of consumer groups). Auto-commit is disabled because we
 * always replay from the beginning at startup and don't need persisted offsets.
 */
class MapEventConsumerLoop(
  private val kafkaProperties: KafkaProperties,
  private val properties: DistributedMapProperties,
  private val topicResolver: TopicResolver,
  private val applier: MapEventApplier,
  private val readiness: MapReadinessTracker,
) : SmartLifecycle, DisposableBean {

  private val log = LoggerFactory.getLogger(MapEventConsumerLoop::class.java)
  private val running = AtomicBoolean(false)
  private val thread: Thread = Thread(::run, "dhm-consumer-loop").apply { isDaemon = true }

  @Volatile
  private var consumer: Consumer<String, String?>? = null

  override fun start() {
    if (!running.compareAndSet(false, true)) return
    if (properties.maps.isEmpty()) {
      log.warn("No maps configured; consumer loop will not start")
      // Still mark every (zero) map as ready so the readiness indicator does not block.
      readiness.markAllReadyIfNoMaps()
      return
    }
    thread.start()
  }

  override fun stop() {
    if (!running.compareAndSet(true, false)) return
    consumer?.wakeup()
    try {
      thread.join(Duration.ofSeconds(5).toMillis())
    } catch (_: InterruptedException) {
      Thread.currentThread().interrupt()
    }
  }

  override fun isRunning(): Boolean = running.get()

  override fun destroy() = stop()

  private fun run() {
    val groupId = "dhm-${properties.nodeId ?: "node"}-${UUID.randomUUID()}"
    val props = HashMap<String, Any>().apply {
      putAll(kafkaProperties.buildConsumerProperties())
      put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
      put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
      put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
      put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
    }

    val topics = topicResolver.allTopics()
    log.info("Starting Kafka consumer loop groupId={} topics={}", groupId, topics)

    KafkaConsumer<String, String?>(props).use { kc ->
      consumer = kc
      try {
        kc.subscribe(topics)
        // Force partition assignment — Kafka does this on first poll.
        kc.poll(Duration.ofMillis(0))
        while (kc.assignment().isEmpty() && running.get()) {
          kc.poll(properties.bootstrap.pollTimeout)
        }
        kc.seekToBeginning(kc.assignment())

        bootstrapRestore(kc)
        steadyState(kc)
      } catch (_: WakeupException) {
        if (running.get()) throw RuntimeException("Unexpected wakeup")
      } finally {
        log.info("Kafka consumer loop stopped")
        consumer = null
      }
    }
  }

  private fun bootstrapRestore(kc: Consumer<String, String?>) {
    val deadline = System.nanoTime() + properties.bootstrap.timeout.toNanos()
    val endOffsets = kc.endOffsets(kc.assignment()).toMutableMap()
    val mapsAtBootstrap = properties.maps.keys

    log.info(
      "Bootstrap restore started for {} partition(s); end offsets snapshot={}",
      endOffsets.size, endOffsets,
    )

    while (running.get() && !partitionsCaughtUp(kc, endOffsets)) {
      if (System.nanoTime() > deadline) {
        log.warn(
          "Bootstrap timeout exceeded ({}); marking remaining maps READY anyway",
          properties.bootstrap.timeout,
        )
        break
      }
      val records: ConsumerRecords<String, String?> = kc.poll(properties.bootstrap.pollTimeout)
      records.forEach { applier.apply(it) }
    }

    // After bootstrap (success or timeout) every configured map is READY: from this point on,
    // remote writes will continue to arrive in the steady-state loop below.
    mapsAtBootstrap.forEach(readiness::markReady)
    log.info("Bootstrap restore completed; READY maps={}", mapsAtBootstrap)
  }

  private fun steadyState(kc: Consumer<String, String?>) {
    while (running.get()) {
      val records = kc.poll(Duration.ofMillis(500))
      records.forEach { applier.apply(it) }
    }
  }

  private fun partitionsCaughtUp(
    kc: Consumer<String, String?>,
    endOffsets: Map<TopicPartition, Long>,
  ): Boolean {
    if (endOffsets.isEmpty()) return true
    return endOffsets.all { (tp, end) ->
      val pos = kc.position(tp)
      pos >= end
    }
  }
}
