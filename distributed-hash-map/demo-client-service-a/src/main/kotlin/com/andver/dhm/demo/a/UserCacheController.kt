package com.andver.dhm.demo.a

import com.andver.dhm.api.DistributedMap
import com.andver.dhm.api.DistributedMapRegistry
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.annotation.PostConstruct

@RestController
@RequestMapping("/users", produces = [MediaType.APPLICATION_JSON_VALUE])
class UserCacheController(
  private val registry: DistributedMapRegistry,
) {

  private lateinit var userCache: DistributedMap<UserProfile>

  @PostConstruct
  fun resolveMap() {
    userCache = registry.get("user-cache", UserProfile::class.java)
  }

  @PutMapping("/{userId}")
  fun put(@PathVariable userId: String, @RequestBody profile: UserProfile): UserProfile {
    val canonical = profile.copy(userId = userId)
    userCache.put(userId, canonical)
    return canonical
  }

  @GetMapping("/{userId}")
  fun get(@PathVariable userId: String): ResponseEntity<UserProfile> {
    val value = userCache.get(userId)
    return if (value != null) ResponseEntity.ok(value) else ResponseEntity.notFound().build()
  }

  @DeleteMapping("/{userId}")
  fun remove(@PathVariable userId: String): ResponseEntity<Void> {
    userCache.remove(userId)
    return ResponseEntity.noContent().build()
  }

  @GetMapping
  fun listAll(): Map<String, UserProfile> = userCache.snapshot()
}
