package com.andver.client.notification.model.client

import com.andver.client.notification.model.client.payload.CreateOrderPayload
import com.andver.client.notification.model.client.payload.MakePaymentPayload

enum class DomainClientEventType(val payloadClazz: Class<out Any>? = null, val topic: String) {
  MAKE_PAYMENT_EVENT(MakePaymentPayload::class.java, "make.payment"),
  CREATE_ORDER_EVENT(CreateOrderPayload::class.java, "create.order"),
  ;

  companion object {
    fun fromString(name: String): DomainClientEventType? {
      return DomainClientEventType.entries.firstOrNull { it.name == name }
    }

    fun fromTopic(topic: String): DomainClientEventType? {
      return DomainClientEventType.entries.firstOrNull { it.topic == topic }
    }
  }
}
