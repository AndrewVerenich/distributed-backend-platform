package com.andver.sharding.hash

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MurmurHash3FunctionTest {

  @Test
  fun `hash is deterministic`() {
    val fn = MurmurHash3Function()

    val h1 = fn.hash("hello")
    val h2 = fn.hash("hello")

    val h3 = fn.hash("world")

    assertTrue(h1 == h2, "Hash must be deterministic for the same input")
    assertTrue(h1 != h3, "Different inputs should generally produce different hashes")
  }

  @Test
  fun `hash fits into unsigned 32-bit range`() {
    val fn = MurmurHash3Function()

    val h = fn.hash("any value")

    // MurmurHash3 x86 32-bit returns unsigned 32-bit masked into a Long.
    assertTrue(h in 0L..0xFFFF_FFFFL)
  }
}

