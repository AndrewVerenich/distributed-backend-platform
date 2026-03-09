package com.andver.counter

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableKafka
@EnableScheduling
class CounterServiceApp

fun main(args: Array<String>) {
  SpringApplication.run(CounterServiceApp::class.java, *args)
}
