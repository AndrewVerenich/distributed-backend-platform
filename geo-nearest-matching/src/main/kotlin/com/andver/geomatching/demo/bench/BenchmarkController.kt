package com.andver.geomatching.demo.bench

import com.andver.geomatching.assign.NearestAssigner
import com.andver.geomatching.demo.api.LoadBenchmarkRequest
import com.andver.geomatching.demo.api.LoadBenchmarkResult
import com.andver.geomatching.model.AssignResult
import com.andver.geomatching.model.NoProviderReason
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@RestController
class BenchmarkController(
  private val assigner: NearestAssigner,
) {

  @PostMapping("/benchmark/load")
  fun load(@RequestBody body: LoadBenchmarkRequest): Mono<LoadBenchmarkResult> {
    val started = System.nanoTime()
    return Flux.range(0, body.requests)
      .flatMap(
        {
          assigner.assign(null, body.lng, body.lat, body.radiusM)
        },
        body.concurrency,
      )
      .collectList()
      .map { results ->
        val assigned = results.count { it is AssignResult.Assigned }
        val noCandidates = results.count {
          it is AssignResult.NoProviders && it.reason == NoProviderReason.NO_CANDIDATES
        }
        val exhausted = results.count {
          it is AssignResult.NoProviders && it.reason == NoProviderReason.CANDIDATE_LIST_EXHAUSTED
        }
        LoadBenchmarkResult(
          requests = body.requests,
          assigned = assigned,
          noCandidates = noCandidates,
          exhausted = exhausted,
          elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis(),
        )
      }
  }
}
