package com.andver.singleflight

import reactor.core.publisher.Mono

/**
 * Coalesces concurrent work for the same [key]: one loader runs, others wait for its result.
 * In-flight only — after completion the entry is removed (not a TTL response cache).
 */
interface Singleflight {
  fun <T : Any> execute(
    key: String,
    type: Class<T>,
    loader: () -> Mono<T>,
  ): Mono<SingleflightOutcome<T>>
}

inline fun <reified T : Any> Singleflight.execute(
  key: String,
  noinline loader: () -> Mono<T>,
): Mono<SingleflightOutcome<T>> = execute(key, T::class.java, loader)
