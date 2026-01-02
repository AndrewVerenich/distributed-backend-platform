package com.andver.client.notification.model.client

import com.andver.client.notification.model.client.payload.CreateOrderPayload
import com.andver.client.notification.model.client.payload.MakePaymentPayload

enum class DomainClientEventType(val payloadClazz: Class<out Any>? = null) {
  MAKE_PAYMENT_EVENT(MakePaymentPayload::class.java),
  CREATE_ORDER_EVENT(CreateOrderPayload::class.java),
  ;

  companion object {
    fun fromString(name: String): DomainClientEventType? {
      return DomainClientEventType.entries.firstOrNull { it.name == name }
    }
  }
}
