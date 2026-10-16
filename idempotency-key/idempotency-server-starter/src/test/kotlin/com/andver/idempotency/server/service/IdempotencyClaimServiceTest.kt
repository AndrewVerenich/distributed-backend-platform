package com.andver.idempotency.server.service

import com.andver.idempotency.api.IdempotencyStatus
import com.andver.idempotency.server.IdempotencyServerProperties
import com.andver.idempotency.server.metrics.NoopIdempotencyServerMetrics
import com.andver.idempotency.server.repository.IdempotencyRecord
import com.andver.idempotency.server.repository.IdempotencyRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class IdempotencyClaimServiceTest {

  private val repository = mockk<IdempotencyRepository>()
  private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
  private val service = IdempotencyClaimService(
    repository = repository,
    properties = IdempotencyServerProperties(lease = Duration.ofSeconds(30)),
    metrics = NoopIdempotencyServerMetrics,
    clock = clock,
  )

  @Test
  fun `first insert proceeds`() {
    every {
      repository.tryInsertStarted("alice", "k1", "hash", any(), any())
    } answers {
      Mono.just(started("hash"))
    }

    StepVerifier.create(service.claim("alice", "k1", "hash"))
      .expectNextMatches { it is ClaimResult.Proceed }
      .verifyComplete()
  }

  @Test
  fun `completed record is replayed`() {
    every { repository.tryInsertStarted(any(), any(), any(), any(), any()) } returns Mono.empty()
    every { repository.find("alice", "k1") } returns Mono.just(completed("hash"))

    StepVerifier.create(service.claim("alice", "k1", "hash"))
      .expectNextMatches { it is ClaimResult.Replay }
      .verifyComplete()
  }

  @Test
  fun `different body on completed key conflicts`() {
    every { repository.tryInsertStarted(any(), any(), any(), any(), any()) } returns Mono.empty()
    every { repository.find("alice", "k1") } returns Mono.just(completed("hash-a"))

    StepVerifier.create(service.claim("alice", "k1", "hash-b"))
      .expectNext(ClaimResult.BodyConflict)
      .verifyComplete()
  }

  @Test
  fun `live started lease returns in progress`() {
    every { repository.tryInsertStarted(any(), any(), any(), any(), any()) } returns Mono.empty()
    every { repository.find("alice", "k1") } returns Mono.just(
      started("hash", leaseUntil = clock.instant().plusSeconds(20)),
    )

    StepVerifier.create(service.claim("alice", "k1", "hash"))
      .expectNextMatches { it is ClaimResult.InProgress }
      .verifyComplete()
  }

  @Test
  fun `expired lease is taken over`() {
    every { repository.tryInsertStarted(any(), any(), any(), any(), any()) } returns Mono.empty()
    every { repository.find("alice", "k1") } returns Mono.just(
      started("hash", leaseUntil = clock.instant().minusSeconds(1)),
    )
    every {
      repository.takeOverExpiredLease("alice", "k1", "hash", any(), any())
    } answers { Mono.just(started("hash")) }

    StepVerifier.create(service.claim("alice", "k1", "hash"))
      .expectNextMatches { it is ClaimResult.Proceed }
      .verifyComplete()

    verify(exactly = 1) { repository.takeOverExpiredLease("alice", "k1", "hash", any(), any()) }
  }

  private fun started(bodyHash: String, leaseUntil: Instant = clock.instant().plusSeconds(30)) =
    IdempotencyRecord(
      owner = "alice",
      key = "k1",
      bodyHash = bodyHash,
      status = IdempotencyStatus.STARTED,
      leaseUntil = leaseUntil,
      requestId = "req-1",
      createdAt = clock.instant(),
    )

  private fun completed(bodyHash: String) =
    IdempotencyRecord(
      owner = "alice",
      key = "k1",
      bodyHash = bodyHash,
      status = IdempotencyStatus.COMPLETED,
      leaseUntil = clock.instant(),
      requestId = "req-1",
      responseStatus = 200,
      responseContentType = "application/json",
      responseBody = """{"ok":true}""".toByteArray(),
      createdAt = clock.instant(),
      completedAt = clock.instant(),
      expiresAt = clock.instant().plusSeconds(3600),
    )
}
