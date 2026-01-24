package com.andver.timeservice.controller

import com.andver.timeservice.model.SyncRequest
import com.andver.timeservice.model.SyncResponse
import com.andver.timeservice.model.TimeResponse
import com.andver.timeservice.service.ClockSynchronizationService
import com.andver.timeservice.service.TimeService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/time")
class TimeController(
  private val timeService: TimeService,
  private val syncService: ClockSynchronizationService
) {
  @GetMapping("/now")
  fun getCurrentTime(): Mono<TimeResponse> {
    return Mono.fromCallable {
      TimeResponse(
        time = timeService.getCurrentTime(),
        uncertainty = timeService.getUncertainty()
      )
    }
  }

  @PostMapping("/sync/start")
  fun startSync(@RequestBody request: SyncRequest): Mono<Long> {
    return Mono.fromCallable { syncService.handleSyncRequest(request) }
  }

  @PostMapping("/sync/complete")
  fun completeSync(
    @RequestParam nodeId: String,
    @RequestParam clientT0: Long,
    @RequestParam clientT3: Long
  ): Mono<SyncResponse> {
    return Mono.fromCallable { syncService.completeSync(nodeId, clientT0, clientT3) }
  }
}

