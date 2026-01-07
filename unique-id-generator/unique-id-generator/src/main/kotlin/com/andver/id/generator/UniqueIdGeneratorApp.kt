package com.andver.id.generator

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class UniqueIdGeneratorApp

fun main(args: Array<String>) {
  SpringApplication.run(UniqueIdGeneratorApp::class.java, *args)
}
