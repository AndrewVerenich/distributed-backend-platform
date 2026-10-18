package com.andver.inbox.service

import com.andver.inbox.handler.InboxEventHandler
import com.andver.inbox.model.InboxEvent
import com.andver.inbox.model.InboxStatus
import com.andver.inbox.repository.InboxRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class DefaultInboxEventProcessorTest {

  private val repository = mockk<InboxRepository>()
  private lateinit var processor: DefaultInboxEventProcessor

  private val idempotencyKey = UUID.randomUUID().toString()
  private val eventType = "booking-requested"
  private val payload = """{"bookingId":"b-1"}"""

  @BeforeEach
  fun setUp() {
    processor = DefaultInboxEventProcessor(repository, null)
  }

  @Test
  fun `process inserts inbox row runs handler and marks PROCESSED`() {
    val handled = AtomicBoolean(false)
    val handler = buildHandler {
      handled.set(true)
      Mono.empty()
    }

    every { repository.insertIfAbsent(idempotencyKey, eventType) } returns Mono.empty()
    every { repository.findLockingByIdempotencyKey(idempotencyKey) } returns
      Mono.just(buildRecord(InboxStatus.RECEIVED))
    every { repository.updateStatus(idempotencyKey, InboxStatus.PROCESSED) } returns Mono.just(1)

    StepVerifier.create(processor.process(handler, idempotencyKey, payload))
      .verifyComplete()

    assert(handled.get())
    verify { repository.updateStatus(idempotencyKey, InboxStatus.PROCESSED) }
  }

  @Test
  fun `process skips already PROCESSED event`() {
    val handled = AtomicBoolean(false)
    val handler = buildHandler {
      handled.set(true)
      Mono.empty()
    }

    every { repository.insertIfAbsent(idempotencyKey, eventType) } returns Mono.empty()
    every { repository.findLockingByIdempotencyKey(idempotencyKey) } returns
      Mono.just(buildRecord(InboxStatus.PROCESSED))

    StepVerifier.create(processor.process(handler, idempotencyKey, payload))
      .verifyComplete()

    assert(!handled.get())
    verify(exactly = 0) { repository.updateStatus(any(), any()) }
  }

  @Test
  fun `process marks FAILED and rethrows when handler errors`() {
    val handler = buildHandler { Mono.error(RuntimeException("boom")) }

    every { repository.insertIfAbsent(idempotencyKey, eventType) } returns Mono.empty()
    every { repository.findLockingByIdempotencyKey(idempotencyKey) } returns
      Mono.just(buildRecord(InboxStatus.RECEIVED))
    every { repository.updateStatus(idempotencyKey, InboxStatus.FAILED) } returns Mono.just(1)

    StepVerifier.create(processor.process(handler, idempotencyKey, payload))
      .expectErrorMessage("boom")
      .verify()

    verify { repository.updateStatus(idempotencyKey, InboxStatus.FAILED) }
    verify(exactly = 0) { repository.updateStatus(idempotencyKey, InboxStatus.PROCESSED) }
  }

  @Test
  fun `process errors when inbox row is missing`() {
    val handler = buildHandler { Mono.empty() }

    every { repository.insertIfAbsent(idempotencyKey, eventType) } returns Mono.empty()
    every { repository.findLockingByIdempotencyKey(idempotencyKey) } returns Mono.empty()

    StepVerifier.create(processor.process(handler, idempotencyKey, payload))
      .expectErrorMatches { it is IllegalStateException && it.message!!.contains("missing") }
      .verify()
  }

  private fun buildRecord(status: InboxStatus) = InboxEvent(
    idempotencyKey = idempotencyKey,
    eventType = eventType,
    status = status,
    receivedAt = LocalDateTime.now(),
  )

  private fun buildHandler(block: () -> Mono<Void>) = object : InboxEventHandler {
    override val eventType = this@DefaultInboxEventProcessorTest.eventType
    override fun handle(payload: String, idempotencyKey: String): Mono<Void> = block()
  }
}
