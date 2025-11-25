package com.andver.dynamicconfig.storage

import java.util.concurrent.ConcurrentHashMap

interface DynamicConfigStorage {
  fun get(key: String): String?
  fun getAll(): Map<String, String>
}

class DefaultDynamicConfigStorage : DynamicConfigStorage {
  private val store: MutableMap<String, String> = ConcurrentHashMap()
  override fun get(key: String): String? {
    return store[key]
  }

  override fun getAll(): Map<String, String> {
    return HashMap(store)
  }

  internal fun put(key: String, value: String) {
    store.put(key, value)
  }
}
