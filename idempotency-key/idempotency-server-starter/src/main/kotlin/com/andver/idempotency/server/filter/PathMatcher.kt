package com.andver.idempotency.server.filter

class PathMatcher(
  private val patterns: List<String>,
) {
  fun matches(path: String): Boolean =
    patterns.any { pattern ->
      path == pattern || path.startsWith(if (pattern.endsWith("/")) pattern else "$pattern/")
    }
}
