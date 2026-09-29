package com.andver.geomatching.demo.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "geo-matching.demo")
data class DemoProperties(
  val simulator: SimulatorProperties = SimulatorProperties(),
  val autoComplete: AutoCompleteProperties = AutoCompleteProperties(),
  val sweeperInterval: Duration = Duration.ofSeconds(5),
  val reconcileInterval: Duration = Duration.ofSeconds(5),
)

data class SimulatorProperties(
  val enabled: Boolean = true,
  val providers: Int = 40,
  val tickInterval: Duration = Duration.ofSeconds(1),
  val stepDeg: Double = 0.002,
  val bbox: BboxProperties = BboxProperties(),
)

data class BboxProperties(
  val minLng: Double = 27.50,
  val maxLng: Double = 27.65,
  val minLat: Double = 53.85,
  val maxLat: Double = 53.95,
)

data class AutoCompleteProperties(
  val enabled: Boolean = true,
  val jobDuration: Duration = Duration.ofSeconds(4),
)
