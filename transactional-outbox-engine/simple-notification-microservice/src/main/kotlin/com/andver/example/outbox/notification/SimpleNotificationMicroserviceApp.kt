package com.andver.example.outbox.notification

import com.andver.outbox.consumer.handler.AbstractOutboxEventHandler
import com.andver.outbox.publisher.model.OutboxEvent
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@SpringBootApplication
class SimpleNotificationMicroserviceApp

fun main(args: Array<String>) {
  SpringApplication.run(SimpleNotificationMicroserviceApp::class.java, *args)
}

@Component
class OrderCreatedHandler : AbstractOutboxEventHandler<OrderCreatedPayload>() {
  override val eventType: String = "order-created"
  override val payloadType = OrderCreatedPayload::class.java

  override fun handle(event: OutboxEvent, payload: OrderCreatedPayload): Mono<Void> {
    log.info("Processing order.created event=$payload, idempotencyKey=${event.idempotencyKey}")
    return Mono.empty()
  }
}

@Component
class OrderDeliveredHandler : AbstractOutboxEventHandler<OrderDeliveredPayload>() {
  override val eventType: String = "order-delivered"
  override val payloadType = OrderDeliveredPayload::class.java

  override fun handle(event: OutboxEvent, payload: OrderDeliveredPayload): Mono<Void> {
    log.info("Processing order.delivered event=$payload, idempotencyKey=${event.idempotencyKey}")
    return Mono.empty()
  }
}

data class OrderCreatedPayload(
  val orderId: Long,
  val userId: Long,
  val totalAmount: Double,
)

data class OrderDeliveredPayload(
  val orderId: Long,
)
