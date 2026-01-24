package com.andver.timeservice.service

import org.springframework.stereotype.Service

// 10 мс неопределенности
private const val UNCERTAINTY = 10L

interface TimeService {
  fun getCurrentTime(): Long
  fun getUncertainty(): Long
}

@Service
class DefaultTimeService : TimeService {

  override fun getCurrentTime(): Long {
    return System.currentTimeMillis()
  }

  override fun getUncertainty(): Long {
    return UNCERTAINTY
  }
}

