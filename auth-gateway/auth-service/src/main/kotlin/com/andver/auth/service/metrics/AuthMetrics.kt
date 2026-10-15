package com.andver.auth.service.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

interface AuthMetrics {
  fun recordLogin(result: String)
  fun recordRefresh(result: String)
  fun recordLogout()
  fun recordFamilyRevoke()
  fun recordValidate(result: String)
}

@Component
class MicrometerAuthMetrics(
  private val registry: MeterRegistry,
) : AuthMetrics {

  private val logins = ConcurrentHashMap<String, Counter>()
  private val refreshes = ConcurrentHashMap<String, Counter>()
  private val validates = ConcurrentHashMap<String, Counter>()
  private val logouts = Counter.builder("ag_auth_logout_total").register(registry)
  private val familyRevokes = Counter.builder("ag_auth_family_revoke_total").register(registry)

  override fun recordLogin(result: String) {
    logins.computeIfAbsent(result) {
      Counter.builder("ag_auth_login_total").tag("result", result).register(registry)
    }.increment()
  }

  override fun recordRefresh(result: String) {
    refreshes.computeIfAbsent(result) {
      Counter.builder("ag_auth_refresh_total").tag("result", result).register(registry)
    }.increment()
  }

  override fun recordLogout() {
    logouts.increment()
  }

  override fun recordFamilyRevoke() {
    familyRevokes.increment()
  }

  override fun recordValidate(result: String) {
    validates.computeIfAbsent(result) {
      Counter.builder("ag_auth_validate_total").tag("result", result).register(registry)
    }.increment()
  }
}
