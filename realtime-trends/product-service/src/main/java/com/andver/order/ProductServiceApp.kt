package com.andver.order

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class ProductServiceApp

fun main(args: Array<String>) {
  SpringApplication.run(ProductServiceApp::class.java, *args)
}