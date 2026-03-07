package com.andver.resource

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class ResourceServiceApp

fun main(args: Array<String>) {
  SpringApplication.run(ResourceServiceApp::class.java, *args)
}
