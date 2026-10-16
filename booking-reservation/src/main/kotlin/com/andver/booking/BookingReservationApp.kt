package com.andver.booking

import com.andver.booking.config.BookingProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(BookingProperties::class)
class BookingReservationApp

fun main(args: Array<String>) {
  runApplication<BookingReservationApp>(*args)
}
