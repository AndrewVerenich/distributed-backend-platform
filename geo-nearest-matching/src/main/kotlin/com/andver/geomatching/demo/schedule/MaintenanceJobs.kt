package com.andver.geomatching.demo.schedule

import com.andver.geomatching.assign.RequestCompleter
import com.andver.geomatching.store.RequestStore
import com.andver.geomatching.sweep.IndexReconciler
import com.andver.geomatching.sweep.StaleSweeper
import com.andver.geomatching.demo.config.DemoProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class MaintenanceJobs(
  private val sweeper: StaleSweeper,
  private val reconciler: IndexReconciler,
  private val requests: RequestStore,
  private val completer: RequestCompleter,
  private val properties: DemoProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(fixedDelayString = "\${geo-matching.demo.sweeper-interval:5000}")
  fun sweep() {
    sweeper.sweep()
      .doOnNext { if (it.evictedProviders > 0) log.info("stale sweep {}", it) }
      .doOnError { log.warn("sweep failed: {}", it.message) }
      .block()
  }

  @Scheduled(fixedDelayString = "\${geo-matching.demo.reconcile-interval:5000}")
  fun reconcile() {
    reconciler.reconcile()
      .doOnNext { if (it.added > 0 || it.removed > 0) log.info("reconcile {}", it) }
      .doOnError { log.warn("reconcile failed: {}", it.message) }
      .block()
  }

  @Scheduled(fixedDelayString = "1000")
  fun autoComplete() {
    val auto = properties.autoComplete
    if (!auto.enabled) return
    val cutoff = Instant.now().minus(auto.jobDuration)
    requests.findAssignedOlderThan(cutoff)
      .flatMap { completer.complete(it.id) }
      .onErrorContinue { err, _ -> log.debug("auto-complete skipped: {}", err.message) }
      .blockLast()
  }
}
