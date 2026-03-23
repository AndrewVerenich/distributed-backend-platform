package com.andver.hash.backend

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class SimpleBackendApp

fun main(args: Array<String>) {
  SpringApplication.run(SimpleBackendApp::class.java, *args)
}
