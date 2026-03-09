package com.andver.counter.aggregator.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "video-view")
data class CounterAggregationProperties(
  val inputTopic: String,
  val outputTopic: String,
  val windowSize: Duration,
  val gracePeriod: Duration,
)
