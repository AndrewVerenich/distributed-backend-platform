package com.andver.outbox.publisher

import com.andver.outbox.publisher.model.OutboxEvent
import com.andver.outbox.publisher.model.OutboxStatus
import com.andver.outbox.publisher.repository.WriteOutboxRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime

class DefaultOutboxPublisherTest {

  private val repository = mockk<WriteOutboxRepository>()
  private lateinit var publisher: DefaultOutboxPublisher

  @BeforeEach
  fun setUp() {
    publisher = DefaultOutboxPublisher(repository)
  }

  @Test
  fun `publish saves event with correct partitioningKey and eventType`() {
    val partitioningKeySlot = slot<String>()
    val eventTypeSlot = slot<String>()

    every {
      repository.saveWithJsonb(
        capture(partitioningKeySlot),
        capture(eventTypeSlot),
        any()
      )
    } returns Mono.just(buildSavedEvent())

    StepVerifier.create(publisher.publish("user-123", "ORDER_CREATED", mapOf("orderId" to 42)))
      .assertNext { event -> assertThat(event).isNotNull() }
      .verifyComplete()

    assertThat(partitioningKeySlot.captured).isEqualTo("user-123")
    assertThat(eventTypeSlot.captured).isEqualTo("ORDER_CREATED")
  }

  @Test
  fun `publish serializes payload to JSON`() {
    val payloadSlot = slot<String>()

    every {
      repository.saveWithJsonb(any(), any(), capture(payloadSlot))
    } returns Mono.just(buildSavedEvent())

    val payload = mapOf("orderId" to 42, "amount" to 99.99)
    publisher.publish("order-1", "ORDER_PLACED", payload).block()

    assertThat(payloadSlot.captured).contains("orderId")
    assertThat(payloadSlot.captured).contains("42")
    assertThat(payloadSlot.captured).contains("99.99")
  }

  @Test
  fun `publish serializes complex nested objects`() {
    val payloadSlot = slot<String>()

    every {
      repository.saveWithJsonb(any(), any(), capture(payloadSlot))
    } returns Mono.just(buildSavedEvent())

    data class Address(val street: String, val city: String)
    data class Order(val id: Long, val address: Address)

    publisher.publish("order-1", "ORDER_SHIPPED", Order(1L, Address("Main St", "NYC"))).block()

    assertThat(payloadSlot.captured).contains("Main St")
    assertThat(payloadSlot.captured).contains("NYC")
  }

  @Test
  fun `publish emits the saved OutboxEvent from repository`() {
    val savedEvent = buildSavedEvent(partitioningKey = "order-777", type = "ORDER_PAID")
    every { repository.saveWithJsonb(any(), any(), any()) } returns Mono.just(savedEvent)

    StepVerifier.create(publisher.publish("order-777", "ORDER_PAID", "{}"))
      .assertNext { event ->
        assertThat(event.partitioningKey).isEqualTo("order-777")
        assertThat(event.type).isEqualTo("ORDER_PAID")
      }
      .verifyComplete()
  }

  @Test
  fun `publish propagates repository errors`() {
    every {
      repository.saveWithJsonb(any(), any(), any())
    } returns Mono.error(RuntimeException("DB connection lost"))

    StepVerifier.create(publisher.publish("k", "EVENT", "{}"))
      .expectError(RuntimeException::class.java)
      .verify()
  }

  @Test
  fun `publish delegates to repository exactly once`() {
    every { repository.saveWithJsonb(any(), any(), any()) } returns Mono.just(buildSavedEvent())

    publisher.publish("k", "EVENT_TYPE", mapOf("key" to "value")).block()

    verify(exactly = 1) { repository.saveWithJsonb(any(), any(), any()) }
  }

  private fun buildSavedEvent(
    partitioningKey: String = "default-key",
    type: String = "SOME_EVENT",
  ) = OutboxEvent(
    id = 1L,
    partitioningKey = partitioningKey,
    type = type,
    payload = """{"data":"ok"}""",
    status = OutboxStatus.PENDING,
    createdAt = LocalDateTime.now(),
  )
}
