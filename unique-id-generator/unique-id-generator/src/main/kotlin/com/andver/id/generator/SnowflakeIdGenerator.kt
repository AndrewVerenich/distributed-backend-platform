package com.andver.id.generator

import com.netflix.discovery.EurekaClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
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

  private var lastTimestamp: Long = -1L
  private var sequence: Long = 0L

  init {
    require(workerId in 0..maxWorkerId) { "workerId out of range" }
    require(datacenterId in 0..maxDatacenterId) { "datacenterId out of range" }
  }

  @Synchronized
  override fun generateId(): Long {
    var timestamp = timeGen()

    if (timestamp < lastTimestamp) {
      throw RuntimeException("Clock moved backwards. Refusing to generate id")
    }

    if (timestamp == lastTimestamp) {
      sequence = (sequence + 1) and sequenceMask
      if (sequence == 0L) {
        timestamp = tilNextMillis(lastTimestamp)
      }
    } else {
      sequence = 0L
    }

    lastTimestamp = timestamp
    return assembleId(timestamp, sequence)
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
