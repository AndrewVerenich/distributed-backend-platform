package com.andver.waitingroom.queue

import com.andver.waitingroom.WaitingRoomProperties
import com.andver.waitingroom.metrics.MicrometerWaitingRoomMetrics
import com.andver.waitingroom.metrics.WaitingRoomMetrics
import com.andver.waitingroom.model.EventMeta
import com.andver.waitingroom.model.JoinOutcome
import com.andver.waitingroom.model.RejectReason
import com.andver.waitingroom.model.VisitorStatus
import com.andver.waitingroom.ticket.RedisTicketService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Integration tests against a real Redis started via `docker run`.
 * Avoids Testcontainers docker-java API skew on newer Docker engines.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisFifoQueueIntegrationTest {

  private lateinit var queue: RedisFifoQueueService
  private lateinit var tickets: RedisTicketService
  private lateinit var admission: AdmissionService
  private lateinit var redisTemplate: ReactiveStringRedisTemplate
  private lateinit var factory: LettuceConnectionFactory

  private var containerId: String? = null
  private var redisPort: Int = 0

  @BeforeAll
  fun startRedis() {
    redisPort = freePort()
    val name = "fifo-wr-it-$redisPort"
    ProcessBuilder(
      "docker", "run", "-d", "--rm",
      "--name", name,
      "-p", "$redisPort:6379",
      "redis:7.2-alpine",
    ).inheritIO().start().waitFor(60, TimeUnit.SECONDS)

    containerId = name
    waitForRedis(redisPort)

    factory = LettuceConnectionFactory("127.0.0.1", redisPort)
    factory.afterPropertiesSet()
    val serializer = StringRedisSerializer()
    val context = RedisSerializationContext.newSerializationContext<String, String>(serializer).build()
    redisTemplate = ReactiveStringRedisTemplate(factory, context)
  }

  @AfterAll
  fun stopRedis() {
    containerId?.let { id ->
      ProcessBuilder("docker", "rm", "-f", id).start().waitFor(30, TimeUnit.SECONDS)
    }
    if (this::factory.isInitialized) {
      factory.destroy()
    }
  }

  @BeforeEach
  fun setUp() {
    redisTemplate.execute<String> { connection ->
      connection.serverCommands().flushAll().then(Mono.empty())
    }.then().block()

    val properties = WaitingRoomProperties(
      ticketTtl = Duration.ofSeconds(30),
      defaultAdmitRate = 10,
      maxQueue = 3,
      lockTtl = Duration.ofSeconds(2),
    )
    val metrics: WaitingRoomMetrics = MicrometerWaitingRoomMetrics(SimpleMeterRegistry())
    queue = RedisFifoQueueService(redisTemplate, properties, metrics)
    tickets = RedisTicketService(redisTemplate, properties, metrics)
    admission = AdmissionService(queue, tickets, metrics)

    queue.updateMeta(EventMeta("sale", admitRate = 10, maxQueue = 3, open = true)).block()
  }

  @Test
  fun `join is idempotent and preserves FIFO order`() {
    StepVerifier.create(queue.join("sale", "a")).expectNextMatches { it is JoinOutcome.Ok }.verifyComplete()
    StepVerifier.create(queue.join("sale", "b")).expectNextMatches { it is JoinOutcome.Ok }.verifyComplete()
    StepVerifier.create(queue.join("sale", "c")).expectNextMatches { it is JoinOutcome.Ok }.verifyComplete()

    StepVerifier.create(queue.join("sale", "a"))
      .expectNextMatches { outcome ->
        outcome is JoinOutcome.Ok &&
          outcome.snapshot.position == 1L &&
          outcome.snapshot.visitorId == "a"
      }
      .verifyComplete()

    StepVerifier.create(queue.status("sale", "b"))
      .expectNextMatches { it.position == 2L && it.status == VisitorStatus.WAITING }
      .verifyComplete()
  }

  @Test
  fun `rejects join when queue is full`() {
    queue.join("sale", "a").block()
    queue.join("sale", "b").block()
    queue.join("sale", "c").block()

    StepVerifier.create(queue.join("sale", "d"))
      .expectNextMatches { it is JoinOutcome.Rejected && it.reason == RejectReason.QUEUE_FULL }
      .verifyComplete()
  }

  @Test
  fun `admit batch issues single-use opaque tickets in FIFO order`() {
    queue.join("sale", "a").block()
    queue.join("sale", "b").block()
    queue.join("sale", "c").block()

    val admitted = admission.admitBatch("sale", 2).collectList().block()!!
    assert(admitted.map { it.visitorId } == listOf("a", "b"))

    val statusA = queue.status("sale", "a").block()!!
    assert(statusA.status == VisitorStatus.ADMITTED)
    assert(!statusA.ticket.isNullOrBlank())

    val first = tickets.consume(statusA.ticket!!).block()
    assert(first != null && first.visitorId == "a")

    StepVerifier.create(tickets.consume(statusA.ticket!!)).verifyComplete()
  }

  @Test
  fun `admit lock allows only one leader`() {
    StepVerifier.create(queue.tryAcquireAdmitLock("sale")).expectNext(true).verifyComplete()
    StepVerifier.create(queue.tryAcquireAdmitLock("sale")).expectNext(false).verifyComplete()
    StepVerifier.create(queue.releaseAdmitLock("sale")).expectNext(true).verifyComplete()
    StepVerifier.create(queue.tryAcquireAdmitLock("sale")).expectNext(true).verifyComplete()
  }

  private fun freePort(): Int = ServerSocket(0).use { it.localPort }

  private fun waitForRedis(port: Int) {
    val deadline = System.currentTimeMillis() + 30_000
    var last: Exception? = null
    while (System.currentTimeMillis() < deadline) {
      try {
        val probe = LettuceConnectionFactory("127.0.0.1", port)
        probe.afterPropertiesSet()
        val template = ReactiveStringRedisTemplate(probe)
        template.opsForValue().get("ping").block(Duration.ofSeconds(2))
        probe.destroy()
        return
      } catch (ex: Exception) {
        last = ex
        Thread.sleep(300)
      }
    }
    throw IllegalStateException("Redis did not become ready on port $port", last)
  }
}
