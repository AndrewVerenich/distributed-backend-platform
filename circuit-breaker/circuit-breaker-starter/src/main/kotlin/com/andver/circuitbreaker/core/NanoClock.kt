package com.andver.circuitbreaker.core

fun interface NanoClock {
  fun nanoTime(): Long

  companion object {
    val SYSTEM: NanoClock = NanoClock { System.nanoTime() }
  }
}

class MutableNanoClock(
  @Volatile var now: Long = 0L,
) : NanoClock {
  override fun nanoTime(): Long = now

  fun advance(nanos: Long) {
    now += nanos
  }

  fun advanceMillis(millis: Long) {
    now += millis * 1_000_000L
  }
}
