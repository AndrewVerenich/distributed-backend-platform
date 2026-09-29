package com.andver.geomatching.assign

import com.andver.geomatching.GeoMatchingProperties
import com.andver.geomatching.metrics.MicrometerGeoMatchingMetrics
import com.andver.geomatching.model.AssignResult
import com.andver.geomatching.model.NoProviderReason
import com.andver.geomatching.model.Provider
import com.andver.geomatching.model.ProviderStatus
import com.andver.geomatching.model.RequestStatus
import com.andver.geomatching.support.FakeProviderStore
import com.andver.geomatching.support.FakeRequestStore
import com.andver.geomatching.support.InMemoryGeoIndex
import com.andver.geomatching.support.PassthroughTransactionalOperator
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class DefaultNearestAssignerTest {
  private val now = Instant.parse("2026-01-01T12:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private lateinit var geo: InMemoryGeoIndex
  private lateinit var providers: FakeProviderStore
  private lateinit var requests: FakeRequestStore
  private lateinit var assigner: DefaultNearestAssigner
  private lateinit var locations: DefaultProviderLocationService

  @BeforeEach
  fun setUp() {
    geo = InMemoryGeoIndex()
    providers = FakeProviderStore()
    requests = FakeRequestStore()
    val properties = GeoMatchingProperties(
      heartbeatTtl = Duration.ofSeconds(15),
      defaultRadiusM = 3000.0,
      candidateCount = 10,
    )
    val metrics = MicrometerGeoMatchingMetrics(SimpleMeterRegistry())
    assigner = DefaultNearestAssigner(
      geo, providers, requests, PassthroughTransactionalOperator(), properties, metrics, clock
    )
    locations = DefaultProviderLocationService(providers, geo, metrics, clock)
  }

  @Test
  fun `assigns nearest free provider and removes them from the index`() {
    seedFree("near", 27.56, 53.90)
    seedFree("far", 27.58, 53.91)

    StepVerifier.create(assigner.assign("r1", 27.56, 53.90, 5000.0))
      .assertNext { result ->
        val assigned = result as AssignResult.Assigned
        assertEquals("near", assigned.providerId)
        assertEquals(RequestStatus.ASSIGNED, assigned.status)
      }
      .verifyComplete()

    assertFalse(geo.contains("near"))
    assertTrue(geo.contains("far"))
    assertEquals(ProviderStatus.BUSY, providers.get("near")?.status)
    assertEquals("r1", providers.get("near")?.currentRequestId)
  }

  @Test
  fun `stale provider in the index is skipped by claim cutoff`() {
    seedFree("stale", 27.56, 53.90, lastSeen = now.minusSeconds(60))
    geo.add("stale", 27.56, 53.90).block()

    StepVerifier.create(assigner.assign("r1", 27.56, 53.90, 5000.0))
      .assertNext { result ->
        val none = result as AssignResult.NoProviders
        assertEquals(NoProviderReason.CANDIDATE_LIST_EXHAUSTED, none.reason)
      }
      .verifyComplete()
  }

  @Test
  fun `busy leftover in GEO loses the race and next candidate is claimed`() {
    seedBusy("ghost", 27.56, 53.90)
    geo.add("ghost", 27.56, 53.90).block()
    seedFree("real", 27.561, 53.901)

    StepVerifier.create(assigner.assign("r1", 27.56, 53.90, 5000.0))
      .assertNext { result ->
        assertEquals("real", (result as AssignResult.Assigned).providerId)
      }
      .verifyComplete()
  }

  @Test
  fun `empty GEO returns no_candidates`() {
    StepVerifier.create(assigner.assign("r1", 27.56, 53.90, 5000.0))
      .assertNext { result ->
        val none = result as AssignResult.NoProviders
        assertEquals(NoProviderReason.NO_CANDIDATES, none.reason)
      }
      .verifyComplete()
  }

  @Test
  fun `two concurrent assigns claim the same provider only once`() {
    seedFree("only", 27.56, 53.90)

    val results = Mono.zip(
      assigner.assign("a", 27.56, 53.90, 5000.0),
      assigner.assign("b", 27.56, 53.90, 5000.0),
    ).block()!!

    val outcomes = listOf(results.t1, results.t2)
    assertEquals(1, outcomes.count { it is AssignResult.Assigned })
    assertEquals(1, outcomes.count { it is AssignResult.NoProviders })
    assertEquals(ProviderStatus.BUSY, providers.get("only")?.status)
  }

  @Test
  fun `complete returns provider to the index`() {
    seedFree("p1", 27.56, 53.90)
    assigner.assign("r1", 27.56, 53.90, 5000.0).block()
    assertFalse(geo.contains("p1"))

    val completer = DefaultRequestCompleter(
      requests, providers, geo, PassthroughTransactionalOperator(), clock
    )
    StepVerifier.create(completer.complete("r1"))
      .assertNext { assertEquals(RequestStatus.COMPLETED, it.status) }
      .verifyComplete()

    assertTrue(geo.contains("p1"))
    assertEquals(ProviderStatus.FREE, providers.get("p1")?.status)
  }

  @Test
  fun `offline location tick revives provider into the index`() {
    providers.put(
      Provider("p1", ProviderStatus.OFFLINE, 27.56, 53.90, now.minusSeconds(60), null)
    )

    StepVerifier.create(locations.updateLocation("p1", 27.57, 53.91))
      .assertNext { assertEquals(ProviderStatus.FREE, it.status) }
      .verifyComplete()

    assertTrue(geo.contains("p1"))
  }

  @Test
  fun `busy location tick updates coords but stays out of the index`() {
    seedFree("p1", 27.56, 53.90)
    assigner.assign("r1", 27.56, 53.90, 5000.0).block()

    locations.updateLocation("p1", 27.57, 53.91).block()
    assertFalse(geo.contains("p1"))
    assertEquals(ProviderStatus.BUSY, providers.get("p1")?.status)
  }

  private fun seedFree(id: String, lng: Double, lat: Double, lastSeen: Instant = now) {
    providers.put(Provider(id, ProviderStatus.FREE, lng, lat, lastSeen, null))
    geo.add(id, lng, lat).block()
  }

  private fun seedBusy(id: String, lng: Double, lat: Double) {
    providers.put(Provider(id, ProviderStatus.BUSY, lng, lat, now, "other"))
  }
}
