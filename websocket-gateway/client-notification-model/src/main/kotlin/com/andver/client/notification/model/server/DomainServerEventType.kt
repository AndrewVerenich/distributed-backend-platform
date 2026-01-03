package com.andver.client.notification.model.server

enum class DomainServerEventType(val type: String) {
  PAYMENT_ACCEPTED_EVENT("payment.accepted"),
  PAYMENT_DECLINED_EVENT("payment.declined"),
  ORDER_DELIVERED("order.delivered");
}
