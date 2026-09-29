package com.andver.geomatching.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

enum class ProviderStatus {
  FREE, BUSY, OFFLINE;

  fun db(): String = name.lowercase()

  companion object {
    fun fromDb(value: String): ProviderStatus = valueOf(value.uppercase())
  }
}

enum class RequestStatus {
  PENDING, ASSIGNED, COMPLETED, NO_PROVIDERS;

  fun db(): String = name.lowercase()

  companion object {
    fun fromDb(value: String): RequestStatus = valueOf(value.uppercase())
  }
}

@Table("providers")
data class Provider(
  @Id val id: String,
  val status: ProviderStatus,
  val lng: Double?,
  val lat: Double?,
  @Column("last_seen_at") val lastSeenAt: Instant?,
  @Column("current_request_id") val currentRequestId: String?,
)

@Table("requests")
data class GeoRequest(
  @Id val id: String,
  val status: RequestStatus,
  val lng: Double,
  val lat: Double,
  @Column("radius_m") val radiusM: Double,
  @Column("provider_id") val providerId: String? = null,
  @Column("distance_m") val distanceM: Double? = null,
  @Column("created_at") val createdAt: Instant? = null,
  @Column("updated_at") val updatedAt: Instant? = null,
)

data class GeoCandidate(
  val providerId: String,
  val distanceM: Double,
)

sealed class AssignResult {
  abstract val requestId: String
  abstract val status: RequestStatus

  data class Assigned(
    override val requestId: String,
    val providerId: String,
    val distanceM: Double,
  ) : AssignResult() {
    override val status: RequestStatus = RequestStatus.ASSIGNED
  }

  data class NoProviders(
    override val requestId: String,
    val reason: NoProviderReason,
  ) : AssignResult() {
    override val status: RequestStatus = RequestStatus.NO_PROVIDERS
  }
}

enum class NoProviderReason {
  NO_CANDIDATES,
  CANDIDATE_LIST_EXHAUSTED,
}

data class CompleteResult(
  val requestId: String,
  val providerId: String?,
  val status: RequestStatus,
)

data class SweepResult(
  val evictedProviders: Int,
  val abandonedRequests: Int,
)

data class ReconcileResult(
  val added: Int,
  val removed: Int,
)

data class IndexStats(
  val redisMembers: Long,
  val pgFreeFresh: Long,
  val drift: Long,
)
