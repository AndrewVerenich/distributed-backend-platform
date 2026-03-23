package com.andver.hash.router

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
class HashRouterServiceApp

fun main(args: Array<String>) {
  SpringApplication.run(HashRouterServiceApp::class.java, *args)
}
