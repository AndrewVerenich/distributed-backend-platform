package com.andver.singleflight.upstream.api

import com.andver.singleflight.upstream.model.Product
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@RestController
class ProductController(
  @Value("\${upstream.delay-ms:100}") private val delayMs: Long,
) {
  private val calls = AtomicLong(0)
  private val fail = AtomicBoolean(false)

  @GetMapping("/products/{id}")
  fun get(@PathVariable id: String): Mono<Product> {
    calls.incrementAndGet()
    if (fail.get()) {
      return Mono.error(ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "forced failure"))
    }
    return Mono.delay(Duration.ofMillis(delayMs))
      .thenReturn(Product(id = id, name = "Product $id"))
  }

  @GetMapping("/admin/calls")
  fun callCount(): Map<String, Long> = mapOf("calls" to calls.get())

  @PostMapping("/admin/calls/reset")
  fun resetCalls(): Map<String, Long> {
    calls.set(0)
    return mapOf("calls" to 0L)
  }

  @PostMapping("/admin/fail")
  fun setFail(@RequestParam enabled: Boolean): Map<String, Boolean> {
    fail.set(enabled)
    return mapOf("fail" to fail.get())
  }
}
