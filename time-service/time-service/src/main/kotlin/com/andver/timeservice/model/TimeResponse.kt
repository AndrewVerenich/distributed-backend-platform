package com.andver.timeservice.model

data class TimeResponse(
  val time: Long,
  val uncertainty: Long = 0
)

