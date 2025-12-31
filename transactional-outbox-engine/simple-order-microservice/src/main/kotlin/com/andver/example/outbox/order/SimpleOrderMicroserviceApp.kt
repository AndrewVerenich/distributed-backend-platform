package com.andver.example.outbox.order

import com.andver.outbox.publisher.OutboxPublisher
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@SpringBootApplication
@EnableR2dbcRepositories
class SimpleOrderMicroserviceApp

fun main(args: Array<String>) {
  SpringApplication.run(SimpleOrderMicroserviceApp::class.java, *args)
}

@RestController
@RequestMapping("/api/orders")
class OrderController(
  private val orderService: OrderService
) {

  @PostMapping
  fun createOrder(
    @RequestBody request: CreateOrderRequest
  ): Mono<ResponseEntity<Order>> {
    return orderService.createOrder(request.userId, request.totalAmount)
      .map { order -> ResponseEntity.status(HttpStatus.CREATED).body(order) }
  }

  @PutMapping("/{orderId}/deliver")
  fun markOrderAsDelivered(
    @PathVariable orderId: Long
  ): Mono<ResponseEntity<Unit>> {
    return orderService.markOrderAsDelivered(orderId)
      .map { ResponseEntity.ok(Unit) }
  }
}

@Service
class OrderService(
  private val orderRepository: OrderRepository,
  private val outboxPublisher: OutboxPublisher,
) {
  private val log = LoggerFactory.getLogger(OrderService::class.java)

  @Transactional
  fun createOrder(userId: Long, totalAmount: Double): Mono<Order> {
    val order = Order(
      userId = userId,
      totalAmount = totalAmount,
      status = OrderStatus.CREATED
    )
    return orderRepository.save(order)
      .flatMap { savedOrder ->
        log.info("Order created: id=${savedOrder.id}, userId=$userId, amount=$totalAmount")
        outboxPublisher.publish(
          partitioningKey = savedOrder.id.toString(),
          eventType = "order-created",
          payload = OrderCreatedPayload(
            orderId = savedOrder.id!!,
            userId = savedOrder.userId,
            totalAmount = savedOrder.totalAmount,
          )
        )
          .thenReturn(savedOrder)
      }
  }

  @Transactional
  fun markOrderAsDelivered(orderId: Long): Mono<Unit> {
    return orderRepository.findById(orderId)
      .flatMap { order ->
        val deliveredAt = LocalDateTime.now()
        orderRepository.updateStatusAndDeliveredAt(orderId, OrderStatus.DELIVERED, deliveredAt)
          .then(
            outboxPublisher.publish(
              partitioningKey = order.id.toString(),
              eventType = "order-delivered",
              payload = OrderDeliveredPayload(
                orderId = order.id!!,
              )
            ).doOnSuccess {
              log.info("Order delivered: id=$orderId")
            }
          ).thenReturn(Unit)
      }
  }
}

@Table("orders")
data class Order(
  @Id
  val id: Long? = null,
  val userId: Long,
  val totalAmount: Double,
  val status: OrderStatus,
  val createdAt: LocalDateTime = LocalDateTime.now(),
  val deliveredAt: LocalDateTime? = null
)

enum class OrderStatus {
  CREATED,
  DELIVERED,
}

data class CreateOrderRequest(
  val userId: Long,
  val totalAmount: Double
)

data class OrderCreatedPayload(
  val orderId: Long,
  val userId: Long,
  val totalAmount: Double,
)

data class OrderDeliveredPayload(
  val orderId: Long,
)

interface OrderRepository : ReactiveCrudRepository<Order, Long> {
  @Query("UPDATE orders SET status = :status, delivered_at = :deliveredAt WHERE id = :id")
  fun updateStatusAndDeliveredAt(
    id: Long,
    status: OrderStatus,
    deliveredAt: LocalDateTime?
  ): Mono<Int>
}