package com.andver.idempotency.demo.charge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoChargeServiceApp

fun main(args: Array<String>) {
  runApplication<DemoChargeServiceApp>(*args)
}
