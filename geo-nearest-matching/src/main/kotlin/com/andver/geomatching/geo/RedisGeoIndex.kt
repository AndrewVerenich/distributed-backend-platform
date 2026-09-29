package com.andver.geomatching.geo

import com.andver.geomatching.GeoMatchingProperties
import com.andver.geomatching.model.GeoCandidate
import org.springframework.data.domain.Range
import org.springframework.data.geo.Distance
import org.springframework.data.geo.Point
import org.springframework.data.redis.connection.RedisGeoCommands
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.domain.geo.GeoReference
import org.springframework.data.redis.domain.geo.GeoShape
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Derived GEO index. Membership itself means "searchable"; status lives in Postgres. */
interface GeoIndex {
  fun add(providerId: String, lng: Double, lat: Double): Mono<Void>
  fun remove(providerId: String): Mono<Void>
  fun search(lng: Double, lat: Double, radiusM: Double, count: Long): Mono<List<GeoCandidate>>
  fun members(): Flux<String>
  fun size(): Mono<Long>
}

@Component
class RedisGeoIndex(
  private val redis: ReactiveStringRedisTemplate,
  private val properties: GeoMatchingProperties,
) : GeoIndex {

  private val key: String get() = properties.geoKey

  override fun add(providerId: String, lng: Double, lat: Double): Mono<Void> =
    redis.opsForGeo().add(key, Point(lng, lat), providerId).then()

  override fun remove(providerId: String): Mono<Void> =
    redis.opsForGeo().remove(key, providerId).then()

  override fun search(lng: Double, lat: Double, radiusM: Double, count: Long): Mono<List<GeoCandidate>> {
    val args = RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
      .includeDistance()
      .sortAscending()
      .limit(count)
    return redis.opsForGeo()
      .search(
        key,
        GeoReference.fromCoordinate(lng, lat),
        GeoShape.byRadius(Distance(radiusM, RedisGeoCommands.DistanceUnit.METERS)),
        args,
      )
      .map { result ->
        GeoCandidate(
          providerId = result.content.name,
          distanceM = result.distance.value,
        )
      }
      .collectList()
  }

  override fun members(): Flux<String> =
    redis.opsForZSet().range(key, Range.unbounded())

  override fun size(): Mono<Long> =
    redis.opsForZSet().size(key).defaultIfEmpty(0)
}
