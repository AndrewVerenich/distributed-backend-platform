package com.andver.clientratelimit.budget

import com.andver.clientratelimit.model.ClientRateLimitRule
import com.andver.clientratelimit.model.TokenBucketDecision
import reactor.core.publisher.Mono
import java.time.Duration

interface TokenBucket {
  fun tryAcquire(rule: ClientRateLimitRule): Mono<TokenBucketDecision>
}

interface CooldownStore {
  fun remaining(ruleName: String): Mono<Duration>

  fun set(ruleName: String, wait: Duration): Mono<Void>
}
