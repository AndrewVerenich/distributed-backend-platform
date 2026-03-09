package com.andver.counter.aggregator

import com.andver.counter.aggregator.properties.CounterAggregationProperties
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.EnableKafkaStreams

@SpringBootApplication
@EnableKafka
@EnableKafkaStreams
@EnableConfigurationProperties(CounterAggregationProperties::class)
class CounterAggregatorApp

fun main(args: Array<String>) {
  SpringApplication.run(CounterAggregatorApp::class.java, *args)
}
