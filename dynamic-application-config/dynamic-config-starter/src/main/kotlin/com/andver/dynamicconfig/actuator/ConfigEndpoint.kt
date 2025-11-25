package com.andver.dynamicconfig.actuator

import com.andver.dynamicconfig.storage.DynamicConfigStorage
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation

@Endpoint(id = "dynamic-config")
class ConfigEndpoint(
  private val storage: DynamicConfigStorage,
) {
  @ReadOperation
  fun config(): Map<String, String> = storage.getAll()
}