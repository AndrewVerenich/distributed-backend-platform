package com.andver.dynamicconfig.engine

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.EnableKafkaStreams

@SpringBootApplication
@EnableKafka
@EnableKafkaStreams
class DynamicConfigEngineApp

fun main(args: Array<String>) {
  SpringApplication.run(DynamicConfigEngineApp::class.java, *args)
}

