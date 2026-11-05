package com.andver.clientratelimit.rules

import com.andver.clientratelimit.ClientRateLimiterProperties
import com.andver.clientratelimit.model.ClientRateLimitRule

class ClientRateLimitRuleSet(
  private val rules: List<ClientRateLimitRule>,
) {
  fun match(path: String): ClientRateLimitRule? {
    var best: ClientRateLimitRule? = null
    var bestLen = -1
    for (rule in rules) {
      for (prefix in rule.pathPrefixes) {
        if (path.startsWith(prefix) && prefix.length > bestLen) {
          best = rule
          bestLen = prefix.length
        }
      }
    }
    return best
  }

  companion object {
    fun from(properties: ClientRateLimiterProperties): ClientRateLimitRuleSet {
      val resolved = properties.rules.map { rule ->
        require(rule.name.isNotBlank()) { "client-rate-limiter rule name must not be blank" }
        require(rule.pathPrefixes.isNotEmpty()) { "rule ${rule.name} needs path-prefixes" }
        require(rule.limit > 0) { "rule ${rule.name} limit must be > 0" }
        require(!rule.window.isZero && !rule.window.isNegative) { "rule ${rule.name} window must be > 0" }
        val burst = if (rule.burst <= 0) rule.limit else rule.burst
        ClientRateLimitRule(
          name = rule.name,
          pathPrefixes = rule.pathPrefixes,
          limit = rule.limit,
          window = rule.window,
          burst = burst,
        )
      }
      return ClientRateLimitRuleSet(resolved)
    }
  }
}
