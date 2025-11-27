package com.andver.dynamicconfig.engine

import com.andver.dynamicconfig.engine.properties.DynamicConfigEngineProperties
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.EnableKafkaStreams

@SpringBootApplication
@EnableKafka
@EnableKafkaStreams
@EnableConfigurationProperties(DynamicConfigEngineProperties::class)
class DynamicConfigEngineApp

fun main(args: Array<String>) {
  SpringApplication.run(DynamicConfigEngineApp::class.java, *args)
}

