package com.andver.ratelimit.rules

import com.andver.ratelimit.RateLimiterProperties
import com.andver.ratelimit.model.ResolvedRule
import org.slf4j.LoggerFactory
import java.time.Duration

class RateLimitRuleSet(
  val rules: List<ResolvedRule>,
  private val excludePrefixes: List<String>,
) {
  fun match(path: String): ResolvedRule? {
    if (excludePrefixes.any { prefix -> prefix.isNotEmpty() && path.startsWith(prefix) }) {
      return null
    }
    val indexed = rules.flatMapIndexed { index, rule ->
      rule.pathPrefixes.map { prefix -> Candidate(prefix, rule, index) }
    }
    return indexed
      .filter { path.startsWith(it.prefix) }
      .maxWithOrNull(compareBy<Candidate> { it.prefix.length }.thenByDescending { it.index })
      ?.rule
  }

  private data class Candidate(val prefix: String, val rule: ResolvedRule, val index: Int)

  companion object {
    private val log = LoggerFactory.getLogger(RateLimitRuleSet::class.java)

    fun from(properties: RateLimiterProperties): RateLimitRuleSet {
      val source = properties.rules.ifEmpty {
        listOf(
          RateLimiterProperties.RuleProperties(
            name = "default",
            pathPrefixes = listOf("/"),
            limit = 100,
            window = Duration.ofSeconds(1),
          ),
        )
      }
      val resolved = source.map { rule -> resolve(properties, rule) }
      val excludes = properties.filter.excludePathPrefixes
      log.info(
        "rate-limiter rules prefix={} defaultAlgorithm={} onRedisError={} sharding={} rules={}",
        properties.keyPrefix,
        properties.algorithm.configName(),
        properties.onRedisError,
        if (properties.sharding.enabled) {
          "on(${properties.sharding.nodes.size})"
        } else {
          "off(${properties.sharding.singleShardName})"
        },
        resolved.joinToString { "${it.name}:${it.algorithm.configName()}:${it.limit}/${it.window.toMillis()}ms/burst=${it.burst}" },
      )
      return RateLimitRuleSet(resolved, excludes)
    }

    private fun resolve(properties: RateLimiterProperties, rule: RateLimiterProperties.RuleProperties): ResolvedRule {
      require(rule.name.isNotBlank()) { "rate-limiter rule name must not be blank" }
      require(rule.pathPrefixes.isNotEmpty() && rule.pathPrefixes.all { it.isNotEmpty() }) {
        "rate-limiter rule '${rule.name}' needs a non-empty path prefix"
      }
      require(rule.limit >= 1) { "rate-limiter rule '${rule.name}' limit must be >= 1" }
      require(!rule.window.isZero && !rule.window.isNegative) {
        "rate-limiter rule '${rule.name}' window must be positive"
      }
      require(rule.burst >= 0) { "rate-limiter rule '${rule.name}' burst must be >= 0" }
      val header = rule.keyHeader?.takeIf { it.isNotBlank() } ?: properties.key.header
      require(header.isNotBlank()) { "rate-limiter rule '${rule.name}' key header must not be blank" }
      return ResolvedRule(
        name = rule.name,
        pathPrefixes = rule.pathPrefixes,
        algorithm = rule.algorithm ?: properties.algorithm,
        limit = rule.limit,
        window = rule.window,
        burst = if (rule.burst > 0) rule.burst else rule.limit,
        keyStrategy = rule.keyStrategy ?: properties.key.strategy,
        keyHeader = header,
      )
    }
  }
}
