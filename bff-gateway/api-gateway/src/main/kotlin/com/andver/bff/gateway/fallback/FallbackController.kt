package com.andver.bff.gateway.fallback

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class FallbackController {

  @RequestMapping("/fallback/web", produces = [MediaType.APPLICATION_JSON_VALUE])
  fun web(): Mono<FallbackErrorResponse> = unavailable("bff-web")

  @RequestMapping("/fallback/mobile", produces = [MediaType.APPLICATION_JSON_VALUE])
  fun mobile(): Mono<FallbackErrorResponse> = unavailable("bff-mobile")

  @RequestMapping("/fallback/admin", produces = [MediaType.APPLICATION_JSON_VALUE])
  fun admin(): Mono<FallbackErrorResponse> = unavailable("bff-admin")

  private fun unavailable(target: String): Mono<FallbackErrorResponse> =
    Mono.just(
      FallbackErrorResponse(
        error = "SERVICE_UNAVAILABLE",
        target = target,
        message = "Circuit breaker open or downstream error. Retry later.",
      ),
    )
}
