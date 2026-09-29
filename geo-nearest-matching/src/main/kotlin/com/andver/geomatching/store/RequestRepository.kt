package com.andver.geomatching.store

import com.andver.geomatching.model.GeoRequest
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface RequestRepository : ReactiveCrudRepository<GeoRequest, String> {

  @Query(
    """
    INSERT INTO requests (id, status, lng, lat, radius_m, provider_id, distance_m, created_at, updated_at)
    VALUES (:id, 'pending', :lng, :lat, :radiusM, NULL, NULL, :at, :at)
    RETURNING id, status, lng, lat, radius_m, provider_id, distance_m, created_at, updated_at
    """,
  )
  fun insertPending(
    @Param("id") id: String,
    @Param("lng") lng: Double,
    @Param("lat") lat: Double,
    @Param("radiusM") radiusM: Double,
    @Param("at") at: Instant,
  ): Mono<GeoRequest>

  @Query(
    """
    UPDATE requests
    SET status = 'assigned', provider_id = :providerId, distance_m = :distanceM, updated_at = :at
    WHERE id = :id AND status = 'pending'
    RETURNING id, status, lng, lat, radius_m, provider_id, distance_m, created_at, updated_at
    """,
  )
  fun markAssigned(
    @Param("id") id: String,
    @Param("providerId") providerId: String,
    @Param("distanceM") distanceM: Double,
    @Param("at") at: Instant,
  ): Mono<GeoRequest>

  @Query(
    """
    UPDATE requests
    SET status = 'no_providers', updated_at = :at
    WHERE id = :id AND status = 'pending'
    RETURNING id, status, lng, lat, radius_m, provider_id, distance_m, created_at, updated_at
    """,
  )
  fun markNoProviders(
    @Param("id") id: String,
    @Param("at") at: Instant,
  ): Mono<GeoRequest>

  @Query(
    """
    UPDATE requests
    SET status = 'completed', updated_at = :at
    WHERE id = :id AND status = 'assigned'
    RETURNING id, status, lng, lat, radius_m, provider_id, distance_m, created_at, updated_at
    """,
  )
  fun markCompleted(
    @Param("id") id: String,
    @Param("at") at: Instant,
  ): Mono<GeoRequest>

  @Query(
    """
    UPDATE requests
    SET status = 'no_providers', updated_at = :at
    WHERE id = :id AND status = 'assigned'
    RETURNING id
    """,
  )
  fun markAbandonedReturningId(
    @Param("id") id: String,
    @Param("at") at: Instant,
  ): Mono<String>

  @Query(
    """
    SELECT id, status, lng, lat, radius_m, provider_id, distance_m, created_at, updated_at
    FROM requests
    WHERE status = 'assigned' AND updated_at < :cutoff
    """,
  )
  fun findAssignedOlderThan(@Param("cutoff") cutoff: Instant): Flux<GeoRequest>
}
