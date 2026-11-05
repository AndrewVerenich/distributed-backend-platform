package com.andver.clientratelimit.upstream

data class UpstreamDecision(
  val allowed: Boolean,
  val rule: String,
  val limit: Long,
  val remaining: Long,
  val resetDeltaSeconds: Long,
  val resetEpochSeconds: Long,
)
