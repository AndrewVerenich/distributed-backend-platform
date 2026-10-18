package com.andver.singleflight

data class SingleflightOutcome<T>(
  val value: T,
  val role: SingleflightRole,
)
