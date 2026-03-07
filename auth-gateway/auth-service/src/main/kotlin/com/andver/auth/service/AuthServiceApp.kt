package com.andver.auth.service

import com.andver.auth.service.properties.JwtProperties
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

@SpringBootApplication
@EnableR2dbcRepositories
@EnableConfigurationProperties(JwtProperties::class)
class AuthServiceApp

fun main(args: Array<String>) {
  SpringApplication.run(AuthServiceApp::class.java, *args)
}
