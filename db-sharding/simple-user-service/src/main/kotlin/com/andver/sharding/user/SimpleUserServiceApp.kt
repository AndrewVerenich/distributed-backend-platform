package com.andver.sharding.user

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SimpleUserServiceApp

fun main(args: Array<String>) {
  runApplication<SimpleUserServiceApp>(*args)
}

