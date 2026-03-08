package com.andver.time.starter.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class DefaultLogicalTimeServiceTest {

  private val offsetProvider = mockk<OffsetProvider>()
  private lateinit var service: DefaultLogicalTimeService

  @BeforeEach
  fun setUp() {
    service = DefaultLogicalTimeService(offsetProvider)
  }

  @Test
  fun `getLogicalTime returns local time when offset is zero`() {
    every { offsetProvider.getOffset() } returns 0L
    val before = System.currentTimeMillis()
    val logicalTime = service.getLogicalTime()
    val after = System.currentTimeMillis()

    assertThat(logicalTime).isBetween(before, after)
  }

  @Test
  fun `getLogicalTime adds positive offset to local time`() {
    val offset = 5_000L
    every { offsetProvider.getOffset() } returns offset

    val before = System.currentTimeMillis()
    val logicalTime = service.getLogicalTime()
    val after = System.currentTimeMillis()

    assertThat(logicalTime).isBetween(before + offset, after + offset)
  }

  @Test
  fun `getLogicalTime subtracts negative offset from local time`() {
    val offset = -3_000L
    every { offsetProvider.getOffset() } returns offset

    val before = System.currentTimeMillis()
    val logicalTime = service.getLogicalTime()
    val after = System.currentTimeMillis()

    assertThat(logicalTime).isBetween(before + offset, after + offset)
  }

  @Test
  fun `getLogicalTime uses exactly the value returned by offsetProvider`() {
    val fixedOffset = 12_345L
    every { offsetProvider.getOffset() } returns fixedOffset

    val localNow = System.currentTimeMillis()
    val logicalTime = service.getLogicalTime()

    assertThat(logicalTime - fixedOffset).isBetween(localNow - 50, localNow + 50)
  }

  @Test
  fun `getLogicalInstant returns Instant derived from getLogicalTime`() {
    val offset = 2_000L
    every { offsetProvider.getOffset() } returns offset

    val before = System.currentTimeMillis() + offset
    val instant = service.getLogicalInstant()
    val after = System.currentTimeMillis() + offset

    assertThat(instant.toEpochMilli()).isBetween(before, after)
    assertThat(instant).isInstanceOf(Instant::class.java)
  }

  @Test
  fun `getLogicalInstant with zero offset equals approximate system clock instant`() {
    every { offsetProvider.getOffset() } returns 0L

    val before = Instant.ofEpochMilli(System.currentTimeMillis())
    val logicalInstant = service.getLogicalInstant()
    val after = Instant.ofEpochMilli(System.currentTimeMillis())

    assertThat(logicalInstant).isBetween(before, after)
  }

  @Test
  fun `getLogicalTime with large positive offset moves time far into the future`() {
    val oneHourMs = 3_600_000L
    every { offsetProvider.getOffset() } returns oneHourMs

    val logicalTime = service.getLogicalTime()

    assertThat(logicalTime).isGreaterThan(System.currentTimeMillis())
  }

  @Test
  fun `getLogicalTime with large negative offset moves time into the past`() {
    val oneHourMs = -3_600_000L
    every { offsetProvider.getOffset() } returns oneHourMs

    val logicalTime = service.getLogicalTime()

    assertThat(logicalTime).isLessThan(System.currentTimeMillis())
  }

  @Test
  fun `getLogicalTime is deterministic for the same offset snapshot`() {
    every { offsetProvider.getOffset() } returns 1_000L

    val t1 = service.getLogicalTime()
    Thread.sleep(5)
    val t2 = service.getLogicalTime()

    assertThat(t2).isGreaterThanOrEqualTo(t1)
  }
}
