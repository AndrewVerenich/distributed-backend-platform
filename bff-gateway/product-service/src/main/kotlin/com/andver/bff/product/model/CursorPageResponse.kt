package com.andver.bff.product.model

data class CursorPageResponse(
  val items: List<ProductResponse>,
  val nextCursor: String?,
)
