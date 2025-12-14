package com.andver.taskrunner

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication
@ConfigurationPropertiesScan
class TaskRunnerApp

fun main(args: Array<String>) {
  SpringApplication.run(TaskRunnerApp::class.java, *args)
}

