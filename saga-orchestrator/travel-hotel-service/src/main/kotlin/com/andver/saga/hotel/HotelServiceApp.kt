package com.andver.saga.hotel

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HotelServiceApp

fun main(args: Array<String>) {
  runApplication<HotelServiceApp>(*args)
}
