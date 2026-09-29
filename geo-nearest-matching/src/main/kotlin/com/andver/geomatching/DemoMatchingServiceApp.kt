package com.andver.geomatching

import com.andver.geomatching.demo.config.DemoProperties
import com.andver.geomatching.store.ProviderRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableR2dbcRepositories(basePackageClasses = [ProviderRepository::class])
@EnableConfigurationProperties(GeoMatchingProperties::class, DemoProperties::class)
class DemoMatchingServiceApp

fun main(args: Array<String>) {
  runApplication<DemoMatchingServiceApp>(*args)
}
