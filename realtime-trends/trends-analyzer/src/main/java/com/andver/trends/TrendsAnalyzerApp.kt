package com.andver.trends

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.EnableKafkaStreams

@SpringBootApplication
@EnableKafka
@EnableKafkaStreams
class TrendsAnalyzerApp

fun main(args: Array<String>) {
  SpringApplication.run(TrendsAnalyzerApp::class.java, *args)
}