package com.andver.id.generator

import com.netflix.appinfo.ApplicationInfoManager
import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.EurekaClient
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SnowflakeIdGeneratorTest {

  private fun buildGenerator(
    datacenterId: Long = 1L,
    instanceId: String = "test-instance-1",
  ): SnowflakeIdGenerator {
    val mockInstanceInfo = mockk<InstanceInfo>()
    val mockAppInfoManager = mockk<ApplicationInfoManager>()
    val mockEurekaClient = mockk<EurekaClient>()

    every { mockInstanceInfo.instanceId } returns instanceId
    every { mockAppInfoManager.info } returns mockInstanceInfo
    every { mockEurekaClient.applicationInfoManager } returns mockAppInfoManager

    return SnowflakeIdGenerator(mockEurekaClient, datacenterId)
  }

  @Test
  fun `generateId returns a positive non-zero Long`() {
    val generator = buildGenerator()
    val id = generator.generateId()

    assertThat(id).isPositive()
  }

  @Test
  fun `generateId produces different IDs on consecutive calls`() {
    val generator = buildGenerator()
    val id1 = generator.generateId()
    val id2 = generator.generateId()

    assertThat(id1).isNotEqualTo(id2)
  }

  @Test
  fun `generateId is monotonically increasing over time`() {
    val generator = buildGenerator()
    val ids = (1..100).map { generator.generateId() }

    for (i in 1 until ids.size) {
      assertThat(ids[i]).isGreaterThan(ids[i - 1])
    }
  }

  @Test
  fun `generateId produces unique IDs in tight loop`() {
    val generator = buildGenerator()
    val count = 10_000
    val ids = (1..count).map { generator.generateId() }.toSet()

    assertThat(ids).hasSize(count)
  }

  @RepeatedTest(3)
  fun `generateId is thread-safe under concurrent load`() {
    val generator = buildGenerator()
    val threadCount = 8
    val idsPerThread = 1_000
    val allIds = ConcurrentHashMap.newKeySet<Long>()
    val barrier = CyclicBarrier(threadCount)
    val executor = Executors.newFixedThreadPool(threadCount)
    val failed = AtomicBoolean(false)

    repeat(threadCount) {
      executor.submit {
        try {
          barrier.await(5, TimeUnit.SECONDS)
          repeat(idsPerThread) {
            val id = generator.generateId()
            if (!allIds.add(id)) {
              failed.set(true)
            }
          }
        } catch (e: Exception) {
          failed.set(true)
        }
      }
    }

    executor.shutdown()
    executor.awaitTermination(30, TimeUnit.SECONDS)

    assertThat(failed.get()).isFalse()
    assertThat(allIds).hasSize(threadCount * idsPerThread)
  }

  @Test
  fun `generated ID encodes datacenter ID in correct bit position`() {
    val datacenterId = 3L
    val generator = buildGenerator(datacenterId = datacenterId, instanceId = "instance-0")
    val id = generator.generateId()

    // Snowflake layout: [41 bits timestamp][5 bits datacenter][5 bits worker][12 bits sequence]
    val datacenterIdShift = 12 + 5  // sequenceBits + workerIdBits
    val datacenterIdMask = 0x1FL    // 5 bits
    val extractedDatacenterId = (id shr datacenterIdShift) and datacenterIdMask

    assertThat(extractedDatacenterId).isEqualTo(datacenterId)
  }

  @Test
  fun `different datacenters produce IDs with different datacenter bits`() {
    val gen1 = buildGenerator(datacenterId = 1L, instanceId = "instance-0")
    val gen2 = buildGenerator(datacenterId = 5L, instanceId = "instance-0")

    val id1 = gen1.generateId()
    val id2 = gen2.generateId()

    val datacenterIdShift = 12 + 5
    val datacenterIdMask = 0x1FL
    val dc1 = (id1 shr datacenterIdShift) and datacenterIdMask
    val dc2 = (id2 shr datacenterIdShift) and datacenterIdMask

    assertThat(dc1).isEqualTo(1L)
    assertThat(dc2).isEqualTo(5L)
  }

  @Test
  fun `timestamp component increases over time`() {
    val generator = buildGenerator()
    val twepoch = 1288834974657L
    val timestampShift = 12 + 5 + 5  // sequenceBits + workerIdBits + datacenterIdBits

    val id1 = generator.generateId()
    Thread.sleep(10)
    val id2 = generator.generateId()

    val ts1 = (id1 ushr timestampShift) + twepoch
    val ts2 = (id2 ushr timestampShift) + twepoch

    assertThat(ts2).isGreaterThanOrEqualTo(ts1)
  }

  @Test
  fun `constructor rejects invalid datacenterId`() {
    assertThrows<IllegalArgumentException> {
      buildGenerator(datacenterId = 32L)
    }
  }

  @Test
  fun `constructor rejects negative datacenterId`() {
    assertThrows<IllegalArgumentException> {
      buildGenerator(datacenterId = -1L)
    }
  }

  @Test
  fun `IDs from same generator are globally sortable by generation order`() {
    val generator = buildGenerator()
    val before = System.currentTimeMillis()
    val id = generator.generateId()
    val after = System.currentTimeMillis()

    val twepoch = 1288834974657L
    val timestampShift = 12 + 5 + 5
    val encodedTimestamp = (id ushr timestampShift) + twepoch

    assertThat(encodedTimestamp).isBetween(before, after + 1)
  }
}
