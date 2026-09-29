package com.andver.geomatching.demo.api

data class LocationRequest(
  val lng: Double,
  val lat: Double,
)

data class StatusRequest(
  val status: String,
)

data class CreateRequestBody(
  val id: String? = null,
  val lng: Double,
  val lat: Double,
  val radiusM: Double? = null,
)

data class AssignResponse(
  val requestId: String,
  val status: String,
  val providerId: String? = null,
  val distanceM: Double? = null,
  val reason: String? = null,
)

data class LoadBenchmarkRequest(
  val requests: Int = 50,
  val concurrency: Int = 20,
  val lng: Double,
  val lat: Double,
  val radiusM: Double? = null,
)

data class LoadBenchmarkResult(
  val requests: Int,
  val assigned: Int,
  val noCandidates: Int,
  val exhausted: Int,
  val elapsedMs: Long,
)
