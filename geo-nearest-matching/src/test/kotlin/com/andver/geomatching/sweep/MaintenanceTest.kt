package com.andver.geomatching.sweep

import com.andver.geomatching.GeoMatchingProperties
import com.andver.geomatching.metrics.MicrometerGeoMatchingMetrics
import com.andver.geomatching.model.GeoRequest
import com.andver.geomatching.model.Provider
import com.andver.geomatching.model.ProviderStatus
import com.andver.geomatching.model.RequestStatus
import com.andver.geomatching.support.FakeProviderStore
import com.andver.geomatching.support.FakeRequestStore
import com.andver.geomatching.support.InMemoryGeoIndex
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class MaintenanceTest {
  private val now = Instant.parse("2026-01-01T12:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val properties = GeoMatchingProperties(heartbeatTtl = Duration.ofSeconds(15))

  @Test
  fun `sweeper marks stale providers offline, removes from GEO and abandons request`() {
    val geo = InMemoryGeoIndex()
    val providers = FakeProviderStore()
    val requests = FakeRequestStore()
    providers.put(
      Provider("p1", ProviderStatus.BUSY, 27.56, 53.90, now.minusSeconds(60), "r1")
    )
    geo.add("p1", 27.56, 53.90).block()
    requests.insertPending(
      GeoRequest("r1", RequestStatus.ASSIGNED, 27.56, 53.90, 1000.0, "p1", 10.0, now, now)
    ).block()

    val sweeper = DefaultStaleSweeper(
      providers, requests, geo, properties, MicrometerGeoMatchingMetrics(SimpleMeterRegistry()), clock
    )

    StepVerifier.create(sweeper.sweep())
      .assertNext { result ->
        assertEquals(1, result.evictedProviders)
        assertEquals(1, result.abandonedRequests)
      }
      .verifyComplete()

    assertFalse(geo.contains("p1"))
    assertEquals(ProviderStatus.OFFLINE, providers.get("p1")?.status)
    assertEquals(RequestStatus.NO_PROVIDERS, requests.get("r1")?.status)
  }

  @Test
  fun `reconcile adds missing free members and removes busy leftovers`() {
    val geo = InMemoryGeoIndex()
    val providers = FakeProviderStore()
    providers.put(Provider("free", ProviderStatus.FREE, 27.56, 53.90, now, null))
    providers.put(Provider("busy", ProviderStatus.BUSY, 27.57, 53.91, now, "r1"))
    geo.add("busy", 27.57, 53.91).block()

    val reconciler = DefaultIndexReconciler(
      providers, geo, properties, MicrometerGeoMatchingMetrics(SimpleMeterRegistry()), clock
    )

    StepVerifier.create(reconciler.reconcile())
      .assertNext { result ->
        assertEquals(1, result.added)
        assertEquals(1, result.removed)
      }
      .verifyComplete()

    assertTrue(geo.contains("free"))
    assertFalse(geo.contains("busy"))

    StepVerifier.create(reconciler.stats())
      .assertNext { stats ->
        assertEquals(1, stats.redisMembers)
        assertEquals(1, stats.pgFreeFresh)
        assertEquals(0, stats.drift)
      }
      .verifyComplete()
  }
}
