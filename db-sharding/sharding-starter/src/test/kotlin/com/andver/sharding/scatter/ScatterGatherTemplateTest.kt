package com.andver.sharding.scatter

import com.andver.sharding.registry.ShardRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.r2dbc.spi.ConnectionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class ScatterGatherTemplateTest {

  @Test
  fun `uses all shard names from registry by default`() {
    val registry = mockk<ShardRegistry>()
    val f1 = mockk<ConnectionFactory>()
    val f2 = mockk<ConnectionFactory>()

    every { registry.allShardNames() } returns listOf("s1", "s2")
    every { registry.connectionFactory("s1") } returns f1
    every { registry.connectionFactory("s2") } returns f2

    val template = ScatterGatherTemplate(registry)

    val query: (String, ConnectionFactory) -> Flux<String> = { shardName, _ ->
      Flux.just(shardName.uppercase())
    }

    StepVerifier.create(template.scatterGather(query = query).collectList())
      .assertNext { result ->
        assertEquals(setOf("S1", "S2"), result.toSet())
      }
      .verifyComplete()

    verify(exactly = 1) { registry.connectionFactory("s1") }
    verify(exactly = 1) { registry.connectionFactory("s2") }
  }

  @Test
  fun `respects explicit shards parameter`() {
    val registry = mockk<ShardRegistry>()
    val f1 = mockk<ConnectionFactory>()

    every { registry.connectionFactory("only") } returns f1

    val template = ScatterGatherTemplate(registry)

    val query: (String, ConnectionFactory) -> Flux<String> = { shardName, _ ->
      Flux.just(shardName)
    }

    StepVerifier.create(template.scatterGather(shards = listOf("only"), query = query))
      .expectNext("only")
      .verifyComplete()

    verify(exactly = 1) { registry.connectionFactory("only") }
  }
}

