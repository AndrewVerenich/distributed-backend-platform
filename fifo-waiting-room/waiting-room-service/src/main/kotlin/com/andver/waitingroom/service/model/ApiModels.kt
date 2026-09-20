package com.andver.waitingroom.service.model

import com.andver.waitingroom.model.VisitorStatus

data class JoinRequest(
  val visitorId: String? = null,
)

data class QueueResponse(
  val eventId: String,
  val visitorId: String,
  val position: Long,
  val etaSeconds: Long?,
  val status: VisitorStatus,
  val ticket: String?,
)

data class EventMetaRequest(
  val admitRate: Int,
  val maxQueue: Int,
  val open: Boolean = true,
)

data class ErrorResponse(
  val error: String,
  val reason: String? = null,
)
