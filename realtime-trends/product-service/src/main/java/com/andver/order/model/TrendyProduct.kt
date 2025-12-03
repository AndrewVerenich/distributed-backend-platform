package com.andver.order.model

data class TrendyProduct(
  val productId: Long,
  val categoryId: Long,
  val views: Long,
  val ts: Long
)