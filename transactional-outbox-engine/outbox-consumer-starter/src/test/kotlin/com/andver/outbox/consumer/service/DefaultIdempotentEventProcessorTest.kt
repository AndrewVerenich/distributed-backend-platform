package com.andver.outbox.consumer.service

import com.andver.outbox.consumer.handler.OutboxEventHandler
import com.andver.outbox.consumer.repository.LockingOutboxRepository
import com.andver.outbox.publisher.model.OutboxEvent
import com.andver.outbox.publisher.model.OutboxStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime
import java.util.*

class DefaultIdempotentEventProcessorTest {

  private val repository = mockk<LockingOutboxRepository>()
  private lateinit var processor: DefaultIdempotentEventProcessor

  private val idempotencyKey = UUID.randomUUID().toString()
  private val eventType = "ORDER_CREATED"

  @BeforeEach
  fun setUp() {
    processor = DefaultIdempotentEventProcessor(repository)
  }

  @Test
  fun `process handles PENDING event and updates status to PROCESSED`() {
    val event = buildEvent(status = OutboxStatus.PENDING)
    val handler = buildHandler(eventType, Mono.just(event))

    every {
      repository.findLockingByIdempotencyKeyAndType(idempotencyKey, eventType)
    } returns Mono.just(event)
    every {
      repository.updateStatusByIdempotencyKeyAndType(idempotencyKey, eventType, OutboxStatus.PROCESSED)
    } returns Mono.just(1)

    StepVerifier.create(processor.process(handler, idempotencyKey))
      .verifyComplete()

    verify {
      repository.updateStatusByIdempotencyKeyAndType(idempotencyKey, eventType, OutboxStatus.PROCESSED)
    }
  }

  @Test
  fun `process skips already-PROCESSED event (idempotency)`() {
    val event = buildEvent(status = OutboxStatus.PROCESSED)
    val handler = buildHandler(eventType, Mono.just(event))

    every {
      repository.findLockingByIdempotencyKeyAndType(idempotencyKey, eventType)
    } returns Mono.just(event)

    StepVerifier.create(processor.process(handler, idempotencyKey))
      .verifyComplete()

    verify(exactly = 0) {
      repository.updateStatusByIdempotencyKeyAndType(any(), any(), any())
    }
  }

  @Test
  fun `process updates status to FAILED when event is not found in outbox`() {
    val handler = buildHandler(eventType, Mono.empty())

    every {
      repository.findLockingByIdempotencyKeyAndType(idempotencyKey, eventType)
    } returns Mono.empty()
    every {
      repository.updateStatusByIdempotencyKeyAndType(idempotencyKey, eventType, OutboxStatus.FAILED)
    } returns Mono.just(1)

    StepVerifier.create(processor.process(handler, idempotencyKey))
      .verifyComplete()

    verify {
      repository.updateStatusByIdempotencyKeyAndType(idempotencyKey, eventType, OutboxStatus.FAILED)
    }
  }

  @Test
  fun `process updates status to FAILED when handler throws`() {
    val event = buildEvent(status = OutboxStatus.PENDING)
    val failingHandler = buildHandler(eventType, Mono.error(RuntimeException("downstream error")))

    every {
      repository.findLockingByIdempotencyKeyAndType(idempotencyKey, eventType)
    } returns Mono.just(event)
    every {
      repository.updateStatusByIdempotencyKeyAndType(idempotencyKey, eventType, OutboxStatus.FAILED)
    } returns Mono.just(1)

    StepVerifier.create(processor.process(failingHandler, idempotencyKey))
      .verifyComplete()

    verify {
      repository.updateStatusByIdempotencyKeyAndType(idempotencyKey, eventType, OutboxStatus.FAILED)
    }
    verify(exactly = 0) {
      repository.updateStatusByIdempotencyKeyAndType(any(), any(), OutboxStatus.PROCESSED)
    }
  }

  @Test
  fun `process passes the outbox event to the handler`() {
    val event = buildEvent(status = OutboxStatus.PENDING, payload = """{"orderId":42}""")
    var capturedEvent: OutboxEvent? = null

    val handler = object : OutboxEventHandler {
      override val eventType = this@DefaultIdempotentEventProcessorTest.eventType
      override fun handleInternal(evt: OutboxEvent): Mono<OutboxEvent> {
        capturedEvent = evt
        return Mono.just(evt)
      }
    }

    every {
      repository.findLockingByIdempotencyKeyAndType(idempotencyKey, eventType)
    } returns Mono.just(event)
    every {
      repository.updateStatusByIdempotencyKeyAndType(idempotencyKey, eventType, OutboxStatus.PROCESSED)
    } returns Mono.just(1)

    processor.process(handler, idempotencyKey).block()

    assert(capturedEvent?.payload == """{"orderId":42}""")
  }

  private fun buildEvent(
    status: OutboxStatus,
    payload: String = """{"data":"test"}""",
  ) = OutboxEvent(
    id = 1L,
    partitioningKey = "order-1",
    type = eventType,
    payload = payload,
    idempotencyKey = idempotencyKey,
    status = status,
    createdAt = LocalDateTime.now(),
  )

  private fun buildHandler(
    type: String,
    handleResult: Mono<OutboxEvent>,
  ) = object : OutboxEventHandler {
    override val eventType = type
    override fun handleInternal(event: OutboxEvent): Mono<OutboxEvent> = handleResult
  }
}
