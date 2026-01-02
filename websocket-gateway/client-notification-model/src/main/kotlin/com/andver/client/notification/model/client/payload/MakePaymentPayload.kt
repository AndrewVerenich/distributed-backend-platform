package com.andver.client.notification.model.client.payload

data class MakePaymentPayload(
  val amount: Double,
  val cardId: Long,
)
