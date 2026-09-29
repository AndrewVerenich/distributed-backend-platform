package com.andver.geomatching

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "geo-matching")
data class GeoMatchingProperties(
  /** Redis GEO key. Members are provider ids; only free+fresh belong here. */
  val geoKey: String = "geo:providers",
  /** Location ticks older than this are stale: claim rejects them, sweeper marks offline. */
  val heartbeatTtl: Duration = Duration.ofSeconds(15),
  val defaultRadiusM: Double = 3000.0,
  /** GEOSEARCH COUNT — exhausted list is not the same as an empty index. */
  val candidateCount: Long = 10,
)
