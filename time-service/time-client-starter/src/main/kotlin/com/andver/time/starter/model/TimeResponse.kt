package com.andver.time.starter.model

data class TimeResponse(
  val time: Long,
  val uncertainty: Long = 0
)

