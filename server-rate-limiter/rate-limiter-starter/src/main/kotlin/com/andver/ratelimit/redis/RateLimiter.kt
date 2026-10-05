package com.andver.ratelimit.redis

import com.andver.ratelimit.model.RateLimitDecision
import com.andver.ratelimit.model.ResolvedRule
import reactor.core.publisher.Mono

/**
 * One check = one Redis Lua script. No in-process counter, so every instance shares the quota.
 */
interface RateLimiter {
  fun check(rule: ResolvedRule, identity: String): Mono<RateLimitDecision>
}
