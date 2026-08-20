package com.andver.gateway.push.api

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/benchmark")
class BenchmarkController(
  private val benchmarkService: BenchmarkService,
) {

  @PostMapping("/run")
  fun run(@RequestBody request: BenchmarkRequest): Mono<BenchmarkResult> =
    benchmarkService.run(request)

  @PostMapping("/compare")
  fun compare(@RequestBody request: BenchmarkComparisonRequest): Mono<BenchmarkComparisonResult> =
    benchmarkService.compare(request)
}
