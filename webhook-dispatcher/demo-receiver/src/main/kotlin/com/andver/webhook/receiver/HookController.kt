package com.andver.webhook.receiver

import com.andver.webhook.core.HmacSigner
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@RestController
class HookController(
  private val props: ReceiverProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val completed = ConcurrentHashMap.newKeySet<String>()
  private val secrets = ConcurrentHashMap(props.pathSecrets)
  private val previousSecrets = ConcurrentHashMap<String, Pair<String, Instant>>()
  private val received = ConcurrentHashMap<String, Int>()

  @PostMapping("/hooks/{mode}")
  fun hook(
    @PathVariable mode: String,
    @RequestHeader("X-Signature") signature: String?,
    @RequestHeader("X-Idempotency-Key") idempotencyKey: String?,
    @RequestHeader("X-Timestamp") timestamp: String?,
    @RequestBody body: String,
  ): Mono<ResponseEntity<Map<String, Any>>> {
    val path = "/hooks/$mode"
    if (signature.isNullOrBlank() || timestamp.isNullOrBlank() || idempotencyKey.isNullOrBlank()) {
      return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "missing headers")))
    }
    if (!verify(path, signature, timestamp, body)) {
      return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "bad signature")))
    }
    if (!completed.add(idempotencyKey)) {
      log.info("dedup replay key={}", idempotencyKey)
      return Mono.just(ResponseEntity.ok(mapOf("status" to "duplicate", "key" to idempotencyKey)))
    }
    received.merge(path, 1) { a, b -> a + b }

    return when (mode) {
      "ok" -> Mono.just(ResponseEntity.ok(mapOf("status" to "ok")))
      "fail" -> {
        completed.remove(idempotencyKey)
        Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("status" to "fail")))
      }
      "bad" -> {
        completed.remove(idempotencyKey)
        Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("status" to "bad")))
      }
      "gone" -> {
        completed.remove(idempotencyKey)
        Mono.just(ResponseEntity.status(HttpStatus.GONE).body(mapOf("status" to "gone")))
      }
      "slow" -> Mono.delay(java.time.Duration.ofMillis(props.slowDelayMs))
        .map { ResponseEntity.ok(mapOf("status" to "slow-ok")) }
      else -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "unknown mode")))
    }
  }

  @PutMapping("/admin/secrets")
  fun putSecret(@RequestBody req: SecretUpdate): Mono<Map<String, String>> {
    val path = req.path
    val current = secrets[path]
    if (current != null && req.keepPrevious && req.previousValidUntil != null) {
      previousSecrets[path] = current to Instant.parse(req.previousValidUntil)
    }
    secrets[path] = req.secret
    return Mono.just(mapOf("path" to path, "status" to "updated"))
  }

  @PostMapping("/admin/reset")
  fun reset(): Mono<Map<String, Any>> {
    completed.clear()
    received.clear()
    return Mono.just(mapOf("status" to "reset"))
  }

  @PostMapping("/admin/stats")
  fun stats(): Mono<Map<String, Any>> =
    Mono.just(
      mapOf(
        "received" to received.toMap(),
        "completedKeys" to completed.size,
      ),
    )

  private fun verify(path: String, signature: String, timestamp: String, body: String): Boolean {
    val candidates = mutableListOf<String>()
    secrets[path]?.let { candidates += it }
    previousSecrets[path]?.let { (secret, until) ->
      if (Instant.now().isBefore(until)) candidates += secret
    }
    return candidates.any { HmacSigner.matches(it, timestamp, body, signature) }
  }

  data class SecretUpdate(
    val path: String,
    val secret: String,
    val keepPrevious: Boolean = false,
    val previousValidUntil: String? = null,
  )
}
