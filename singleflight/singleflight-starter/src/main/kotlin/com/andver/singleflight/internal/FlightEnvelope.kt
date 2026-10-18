package com.andver.singleflight.internal

/**
 * Wire format for shared in-flight results (local sink + Redis).
 * [retry] means leader failed without sharing the error — waiters should re-elect.
 */
data class FlightEnvelope(
  val ok: Boolean,
  val payloadJson: String? = null,
  val errorMessage: String? = null,
  val errorClass: String? = null,
  val retry: Boolean = false,
) {
  companion object {
    fun success(payloadJson: String) = FlightEnvelope(ok = true, payloadJson = payloadJson)

    fun error(message: String, errorClass: String) = FlightEnvelope(
      ok = false,
      errorMessage = message,
      errorClass = errorClass,
    )

    fun retry() = FlightEnvelope(ok = false, retry = true)
  }
}

class SingleflightSharedException(
  message: String,
  val errorClass: String,
) : RuntimeException(message)
