package com.andver.clientdeduplicator.starter.cache

import com.andver.clientdeduplicator.starter.properties.CacheProperties
import com.andver.clientdeduplicator.starter.properties.CacheRule
import org.springframework.util.AntPathMatcher

interface CacheRuleMatcher {
  fun findRule(method: String, path: String): CacheRule?
}

class DefaultCacheRuleMatcher(
  private val props: CacheProperties
) : CacheRuleMatcher {
  private val matcher = AntPathMatcher()

  override fun findRule(method: String, path: String): CacheRule? {
    return props.rules.firstOrNull { rule ->
      rule.method.equals(method, ignoreCase = true) && matcher.match(
        rule.url,
        path
      )
    }
  }
}