package com.andver.gateway.config

import com.andver.gateway.converter.JwtAuthenticationConverter
import com.andver.gateway.filter.JwtAuthenticationFilter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
  private val jwtAuthenticationConverter: JwtAuthenticationConverter,
  private val meterRegistry: MeterRegistry
) {

  @Bean
  fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
    return http
      .csrf { it.disable() }
      .httpBasic { it.disable() }
      .formLogin { it.disable() }
      .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
      .authorizeExchange {
        it.pathMatchers("/auth/**").permitAll()
          .pathMatchers("/actuator/**").permitAll()
          .anyExchange().authenticated()
      }
      .addFilterAt(
        JwtAuthenticationFilter(jwtAuthenticationConverter, meterRegistry),
        SecurityWebFiltersOrder.AUTHENTICATION
      )
      .build()
  }
}
