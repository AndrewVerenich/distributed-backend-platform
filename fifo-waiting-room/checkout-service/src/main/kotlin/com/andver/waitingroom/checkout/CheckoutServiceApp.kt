package com.andver.waitingroom.checkout

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CheckoutServiceApp

fun main(args: Array<String>) {
  runApplication<CheckoutServiceApp>(*args)
}
