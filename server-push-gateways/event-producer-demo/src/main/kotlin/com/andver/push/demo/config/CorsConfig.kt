package com.andver.push.demo.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig {
  @Bean
  fun corsWebFilter(): CorsWebFilter {
    val config = CorsConfiguration().apply {
      addAllowedOriginPattern("*")
      addAllowedHeader("*")
      addAllowedMethod("*")
    }
    val source = UrlBasedCorsConfigurationSource()
    source.registerCorsConfiguration("/**", config)
    return CorsWebFilter(source)
  }
}
