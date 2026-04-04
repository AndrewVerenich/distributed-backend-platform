package com.andver.sharding.context

import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class ShardContextTest {

  @Test
  fun `returns empty when shard is not present in context`() {
    StepVerifier.create(ShardContext.currentShard())
      .verifyComplete()
  }

  @Test
  fun `returns shard name from reactor context`() {
    StepVerifier
      .create(
        Mono.just(1)
          .flatMap { ShardContext.currentShard() }
          .contextWrite { ctx -> ctx.put(ShardContext.SHARD_KEY, "shard-1") },
      )
      .expectNext("shard-1")
      .verifyComplete()
  }
}

