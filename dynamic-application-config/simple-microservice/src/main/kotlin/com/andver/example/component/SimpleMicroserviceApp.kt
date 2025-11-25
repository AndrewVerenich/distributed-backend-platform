package com.andver.example.component

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class SimpleMicroserviceApp

fun main(args: Array<String>) {
  SpringApplication.run(SimpleMicroserviceApp::class.java, *args)
}