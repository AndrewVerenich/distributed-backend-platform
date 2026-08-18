package com.andver.cache.api

enum class EvictionPolicyType {
  FIFO,
  LRU,
  LFU,
  CLOCK,
  W_TINY_LFU,
}
