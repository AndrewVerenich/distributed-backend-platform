package com.andver.geomatching.store

import com.andver.geomatching.model.Provider
import com.andver.geomatching.model.ProviderStatus
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface ProviderRepository : ReactiveCrudRepository<Provider, String> {

  @Query(
    """
    INSERT INTO providers (id, status, lng, lat, last_seen_at, current_request_id)
    VALUES (:id, 'free', :lng, :lat, :at, NULL)
    ON CONFLICT (id) DO UPDATE SET
      lng = EXCLUDED.lng,
      lat = EXCLUDED.lat,
      last_seen_at = EXCLUDED.last_seen_at,
      status = CASE
        WHEN providers.status = 'offline' THEN 'free'
        ELSE providers.status
      END
    RETURNING id, status, lng, lat, last_seen_at, current_request_id
    """,
  )
  fun upsertLocation(
    @Param("id") id: String,
    @Param("lng") lng: Double,
    @Param("lat") lat: Double,
    @Param("at") at: Instant,
  ): Mono<Provider>

  @Query(
    """
    UPDATE providers
    SET status = 'busy', current_request_id = :requestId
    WHERE id = :id
      AND status = 'free'
      AND last_seen_at > :cutoff
    RETURNING id, status, lng, lat, last_seen_at, current_request_id
    """,
  )
  fun tryClaim(
    @Param("id") providerId: String,
    @Param("requestId") requestId: String,
    @Param("cutoff") cutoff: Instant,
  ): Mono<Provider>

  @Query(
    """
    UPDATE providers
    SET status = 'free', current_request_id = NULL
    WHERE id = :id
    RETURNING id, status, lng, lat, last_seen_at, current_request_id
    """,
  )
  fun markFreeById(@Param("id") providerId: String): Mono<Provider>

  @Query(
    """
    UPDATE providers
    SET status = 'free', current_request_id = NULL
    WHERE id = :id AND current_request_id = :requestId
    RETURNING id, status, lng, lat, last_seen_at, current_request_id
    """,
  )
  fun markFreeIfAssigned(
    @Param("id") providerId: String,
    @Param("requestId") requestId: String,
  ): Mono<Provider>

  @Query(
    """
    UPDATE providers
    SET status = :status, current_request_id = NULL
    WHERE id = :id
    RETURNING id, status, lng, lat, last_seen_at, current_request_id
    """,
  )
  fun setStatusClearRequest(
    @Param("id") id: String,
    @Param("status") status: ProviderStatus,
  ): Mono<Provider>

  @Query(
    """
    UPDATE providers
    SET status = :status
    WHERE id = :id
    RETURNING id, status, lng, lat, last_seen_at, current_request_id
    """,
  )
  fun setStatus(
    @Param("id") id: String,
    @Param("status") status: ProviderStatus,
  ): Mono<Provider>

  @Query(
    """
    WITH stale AS (
      SELECT id, current_request_id AS old_request_id
      FROM providers
      WHERE status <> 'offline' AND last_seen_at < :cutoff
    ),
    updated AS (
      UPDATE providers p
      SET status = 'offline', current_request_id = NULL
      FROM stale
      WHERE p.id = stale.id
      RETURNING p.id, p.status, p.lng, p.lat, p.last_seen_at, stale.old_request_id AS current_request_id
    )
    SELECT id, status, lng, lat, last_seen_at, current_request_id FROM updated
    """,
  )
  fun markStaleOffline(@Param("cutoff") cutoff: Instant): Flux<Provider>

  @Query(
    """
    SELECT id, status, lng, lat, last_seen_at, current_request_id
    FROM providers
    WHERE status = 'free'
      AND last_seen_at > :cutoff
      AND lng IS NOT NULL
      AND lat IS NOT NULL
    """,
  )
  fun findFreeFresh(@Param("cutoff") cutoff: Instant): Flux<Provider>

  @Query(
    """
    SELECT COUNT(*)
    FROM providers
    WHERE status = 'free'
      AND last_seen_at > :cutoff
      AND lng IS NOT NULL
      AND lat IS NOT NULL
    """,
  )
  fun countFreeFresh(@Param("cutoff") cutoff: Instant): Mono<Long>
}
