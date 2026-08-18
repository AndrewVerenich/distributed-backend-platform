package com.andver.cache.demo.api

import com.andver.cache.demo.model.CatalogItem
import com.andver.cache.demo.service.CatalogCacheService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
class CatalogController(
  private val service: CatalogCacheService,
) {
  @GetMapping("/products/{id}")
  fun get(@PathVariable id: String): ResponseEntity<CatalogItem> =
    service.getById(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

  @GetMapping("/cache/stats")
  fun stats() = service.currentStats()

  @PostMapping("/benchmark")
  fun benchmark(@RequestBody request: BenchmarkRequest): BenchmarkResult =
    service.benchmark(request)

  @PostMapping("/benchmark/compare")
  fun compare(@RequestBody request: BenchmarkComparisonRequest): BenchmarkComparisonResult =
    service.compare(request)
}
