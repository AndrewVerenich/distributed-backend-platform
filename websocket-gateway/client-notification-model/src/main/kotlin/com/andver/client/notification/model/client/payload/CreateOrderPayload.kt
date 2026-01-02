package com.andver.client.notification.model.client.payload

data class CreateOrderPayload(
  val productId: Long,
  val amount: Long,
)
