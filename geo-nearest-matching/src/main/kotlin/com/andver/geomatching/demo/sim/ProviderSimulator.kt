package com.andver.geomatching.demo.sim

import com.andver.geomatching.assign.ProviderLocationService
import com.andver.geomatching.demo.config.DemoProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max
import kotlin.math.min

@Component
class ProviderSimulator(
  private val locations: ProviderLocationService,
  private val properties: DemoProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val positions = ConcurrentHashMap<String, Pair<Double, Double>>()
  @Volatile private var started = false

  @Scheduled(fixedDelayString = "\${geo-matching.demo.simulator.tick-interval:1000}")
  fun tick() {
    val sim = properties.simulator
    if (!sim.enabled) return
    if (!started) {
      bootstrap()
      started = true
    }
    val rng = ThreadLocalRandom.current()
    positions.forEach { (id, pos) ->
      val lng = clamp(pos.first + (rng.nextDouble() - 0.5) * 2 * sim.stepDeg, sim.bbox.minLng, sim.bbox.maxLng)
      val lat = clamp(pos.second + (rng.nextDouble() - 0.5) * 2 * sim.stepDeg, sim.bbox.minLat, sim.bbox.maxLat)
      positions[id] = lng to lat
      locations.updateLocation(id, lng, lat)
        .doOnError { err -> log.debug("location tick failed for {}: {}", id, err.message) }
        .subscribe()
    }
  }

  private fun bootstrap() {
    val sim = properties.simulator
    val rng = ThreadLocalRandom.current()
    repeat(sim.providers) { i ->
      val id = "sim-%04d".format(i + 1)
      val lng = rng.nextDouble(sim.bbox.minLng, sim.bbox.maxLng)
      val lat = rng.nextDouble(sim.bbox.minLat, sim.bbox.maxLat)
      positions[id] = lng to lat
      locations.updateLocation(id, lng, lat)
        .doOnError { err -> log.warn("bootstrap failed for {}: {}", id, err.message) }
        .subscribe()
    }
    log.info("simulator started with {} providers", sim.providers)
  }

  private fun clamp(value: Double, minV: Double, maxV: Double): Double = min(maxV, max(minV, value))
}
