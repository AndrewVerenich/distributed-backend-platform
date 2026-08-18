package com.andver.cache.demo.workload

interface WorkloadGenerator {
  fun nextKey(): String
}
