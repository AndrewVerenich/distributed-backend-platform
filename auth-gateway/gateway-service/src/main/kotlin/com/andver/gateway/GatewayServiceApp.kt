package com.andver.gateway

import com.andver.gateway.properties.JwtProperties
import com.andver.gateway.properties.RoutingProperties
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class, RoutingProperties::class)
class GatewayServiceApp

fun main(args: Array<String>) {
  SpringApplication.run(GatewayServiceApp::class.java, *args)
}
