package com.andver.dhm.api

/**
 * Lookup for all [DistributedMap]s configured in the application context.
 *
 * Each map is created at startup based on `distributed.map.maps.<name>.*` properties and
 * registered here so that user code can pick a map by name and request a typed view.
 */
interface DistributedMapRegistry {

  /** All map names known to this node. */
  fun names(): Set<String>

  /**
   * Returns the [DistributedMap] for [name], casting it to the requested [valueType].
   * Throws if the map is not registered or if [valueType] does not match the configured type.
   */
  fun <V : Any> get(name: String, valueType: Class<V>): DistributedMap<V>

  /** Convenience non-typed accessor used by diagnostics. Returns `null` if not registered. */
  fun raw(name: String): DistributedMap<*>?
}
