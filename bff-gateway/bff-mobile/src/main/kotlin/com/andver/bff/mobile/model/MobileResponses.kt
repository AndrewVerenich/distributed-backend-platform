package com.andver.bff.mobile.model

import java.math.BigDecimal

data class MobileProfileCompact(
  val id: Long,
  val name: String,
)

data class MobileProductCompact(
  val id: Long,
  val name: String,
  val price: BigDecimal,
  val thumbnailUrl: String,
)

data class MobileFeedResponse(
  val profile: MobileProfileCompact,
  val highlights: List<MobileProductCompact>,
  val feed: List<MobileProductCompact>,
  val feedNextCursor: String?,
)

data class MobileProductCursorResponse(
  val items: List<MobileProductCompact>,
  val nextCursor: String?,
)
