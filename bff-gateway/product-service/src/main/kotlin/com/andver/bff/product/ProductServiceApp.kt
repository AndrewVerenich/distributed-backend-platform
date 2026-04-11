package com.andver.bff.product

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ProductServiceApp

fun main(args: Array<String>) {
  runApplication<ProductServiceApp>(*args)
}
