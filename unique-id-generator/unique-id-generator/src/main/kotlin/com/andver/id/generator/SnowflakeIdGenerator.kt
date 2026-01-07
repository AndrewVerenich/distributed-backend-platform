package com.andver.id.generator

import com.netflix.discovery.EurekaClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

interface IdGenerator {
  fun generateId(): Long
}

@Component
class SnowflakeIdGenerator(
  eurekaClient: EurekaClient,
  @Value("\${data-center.id}") private val datacenterId: Long,
) : IdGenerator {

  private var workerId: Long = abs(eurekaClient.applicationInfoManager.info.instanceId.hashCode() % 32).toLong()

  private val twepoch = 1288834974657L

  private val workerIdBits = 5L
  private val datacenterIdBits = 5L
  private val sequenceBits = 12L

  private val maxWorkerId = -1L xor (-1L shl workerIdBits.toInt())
  private val maxDatacenterId = -1L xor (-1L shl datacenterIdBits.toInt())
  private val sequenceMask = -1L xor (-1L shl sequenceBits.toInt())

  private val workerIdShift = sequenceBits
  private val datacenterIdShift = sequenceBits + workerIdBits
  private val timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits

  private val lastTimestamp = AtomicLong(-1L)
  private val sequence = AtomicLong(0L)

  init {
    require(workerId in 0..maxWorkerId) { "workerId out of range" }
    require(datacenterId in 0..maxDatacenterId) { "datacenterId out of range" }
  }

  override fun generateId(): Long {
    while (true) {
      var timestamp = timeGen()
      val lastTs = lastTimestamp.get()

      if (timestamp < lastTs) {
        throw RuntimeException("Clock moved backwards. Refusing to generate id")
      }

      if (timestamp == lastTs) {
        val seq = (sequence.incrementAndGet()) and sequenceMask
        if (seq == 0L) {
          timestamp = tilNextMillis(lastTs)
          sequence.set(0)
        }
        if (lastTimestamp.compareAndSet(lastTs, timestamp)) {
          return assembleId(timestamp, seq)
        }
      } else {
        sequence.set(0)
        if (lastTimestamp.compareAndSet(lastTs, timestamp)) {
          return assembleId(timestamp, 0)
        }
      }
    }
  }

  private fun assembleId(timestamp: Long, seq: Long): Long {
    return ((timestamp - twepoch) shl timestampLeftShift.toInt()) or
        (datacenterId shl datacenterIdShift.toInt()) or
        (workerId shl workerIdShift.toInt()) or
        seq
  }

  private fun tilNextMillis(lastTs: Long): Long {
    var ts = timeGen()
    while (ts <= lastTs) {
      ts = timeGen()
    }
    return ts
  }

  private fun timeGen(): Long = System.currentTimeMillis()
}
