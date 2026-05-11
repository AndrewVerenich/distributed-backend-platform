package com.andver.dhm.runtime

import com.andver.dhm.api.DistributedMap
import com.andver.dhm.api.DistributedMapRegistry

class MapRuntimeRegistry(
  private val mapsByName: Map<String, DistributedMapImpl<*>>,
) : DistributedMapRegistry {

  override fun names(): Set<String> = mapsByName.keys

  @Suppress("UNCHECKED_CAST")
  override fun <V : Any> get(name: String, valueType: Class<V>): DistributedMap<V> {
    val map = mapsByName[name]
      ?: error("Distributed map '$name' is not configured (check distributed.map.maps.* properties)")
    require(map.valueType == valueType) {
      "Distributed map '$name' is configured for valueType=${map.valueType.name} " +
          "but caller requested ${valueType.name}"
    }
    return map as DistributedMap<V>
  }

  override fun raw(name: String): DistributedMap<*>? = mapsByName[name]

  fun localStateOrNull(name: String): LocalState<*>? = mapsByName[name]?.localState

  fun all(): Map<String, DistributedMapImpl<*>> = mapsByName
}
