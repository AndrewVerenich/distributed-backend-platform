package com.andver.waitingroom.service.worker

import com.andver.waitingroom.queue.AdmissionService
import com.andver.waitingroom.queue.FifoQueueService
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class AdmitWorker(
  private val queue: FifoQueueService,
  private val admission: AdmissionService,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private var subscription: Disposable? = null

  @PostConstruct
  fun start() {
    subscription = Flux.interval(Duration.ofSeconds(1))
      .flatMap {
        queue.listEventIds()
          .distinct()
          .flatMap({ eventId -> admitEvent(eventId) }, 4)
          .then()
          .onErrorResume { ex ->
            log.warn("Admit tick failed: {}", ex.message)
            Mono.empty()
          }
      }
      .subscribe()
  }

  @PreDestroy
  fun stop() {
    subscription?.dispose()
  }

  private fun admitEvent(eventId: String): Mono<Void> =
    queue.getMeta(eventId)
      .filter { it.open }
      .flatMapMany { meta -> admission.tryAdmitUnderLock(eventId, meta.admitRate) }
      .doOnNext { admitted ->
        log.debug("Admitted {} to {} ticket={}", admitted.visitorId, admitted.eventId, admitted.ticketId)
      }
      .then()
}
