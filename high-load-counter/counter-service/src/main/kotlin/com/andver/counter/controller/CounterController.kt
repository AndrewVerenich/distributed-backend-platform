package com.andver.counter.controller

import com.andver.counter.model.VideoCounter
import com.andver.counter.service.CounterService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/counters")
class CounterController(private val counterService: CounterService) {

  @GetMapping("/{videoId}")
  fun getCounter(@PathVariable videoId: Long): Mono<VideoCounter> {
    return counterService.getCounter(videoId)
  }

  @PostMapping("/{videoId}/view")
  fun recordView(
    @PathVariable videoId: Long,
    @RequestParam userId: Long,
  ): Mono<Void> {
    return counterService.recordView(videoId, userId)
  }
}
