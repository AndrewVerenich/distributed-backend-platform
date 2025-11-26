package com.andver.example.component

import com.andver.dynamicconfig.storage.DynamicConfigStorage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class SimpleMicroserviceApp

fun main(args: Array<String>) {
  SpringApplication.run(SimpleMicroserviceApp::class.java, *args)
}

@RestController
@RequestMapping("/configs")
class SimpleController(@Autowired private val storage: DynamicConfigStorage) {

  @GetMapping("/{key}")
  fun getConfig(@PathVariable key: String): KeyValue {
    return KeyValue(key, storage.get(key))
  }
}

data class KeyValue(
  val key: String,
  val value: String?,
)
