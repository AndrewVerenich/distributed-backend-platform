package com.andver.example.client.notification.payment

import com.andver.client.notification.handler.AbstractClientNotificationEventHandler
import com.andver.client.notification.model.client.payload.MakePaymentPayload
import com.andver.client.notification.model.server.DomainServerEvent
import com.andver.client.notification.model.server.DomainServerEventType
import com.andver.client.notification.model.server.payload.PaymentDeclinedPayload
import com.andver.client.notification.sender.ClientNotificationSender
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@SpringBootApplication
class SimplePaymentMicroserviceApp

fun main(args: Array<String>) {
  SpringApplication.run(SimplePaymentMicroserviceApp::class.java, *args)
}

@Component
class MakePaymentHandler(
  private val clientNotificationSender: ClientNotificationSender,
) : AbstractClientNotificationEventHandler<MakePaymentPayload>() {
  override val eventType: String = "make.payment"
  override val payloadType = MakePaymentPayload::class.java

  override fun handle(userId: Long, payload: MakePaymentPayload?): Mono<Unit> {
    log.info("Processing make.payment client event=$payload")
    log.info("Decline payment by credit cardId=${payload?.cardId}")
    return clientNotificationSender.send(
      DomainServerEvent(
        type = DomainServerEventType.PAYMENT_DECLINED_EVENT.type,
        userId = userId,
        payload = PaymentDeclinedPayload("Expired card"),
      )
    )
  }
}

