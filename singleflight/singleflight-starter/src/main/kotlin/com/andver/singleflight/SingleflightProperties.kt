package com.andver.singleflight

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "singleflight")
data class SingleflightProperties(
  val enabled: Boolean = true,
  val mode: SingleflightMode = SingleflightMode.LOCAL,
  /** When true, a failed leader shares the error with waiters. When false, waiters may re-elect. */
  val shareErrors: Boolean = true,
  val waiterTimeout: Duration = Duration.ofSeconds(5),
  val namespace: String = "default",
  val distributed: Distributed = Distributed(),
) {
  data class Distributed(
    /** Leader lease in Redis. Expired lease allows failover. */
    val lease: Duration = Duration.ofSeconds(10),
    /** How long the result key lives for late waiters. */
    val resultTtl: Duration = Duration.ofSeconds(30),
    /** Poll interval while waiting for a leader result. */
    val pollInterval: Duration = Duration.ofMillis(25),
  )
}
