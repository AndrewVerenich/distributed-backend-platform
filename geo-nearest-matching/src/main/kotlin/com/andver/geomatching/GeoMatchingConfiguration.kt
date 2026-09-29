package com.andver.geomatching

import com.andver.geomatching.store.geoMatchingR2dbcConversions
import io.r2dbc.spi.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator
import java.time.Clock

@Configuration
class GeoMatchingConfiguration {

  @Bean
  fun geoMatchingClock(): Clock = Clock.systemUTC()

  @Bean
  fun geoMatchingSchema(connectionFactory: ConnectionFactory): ConnectionFactoryInitializer {
    val initializer = ConnectionFactoryInitializer()
    initializer.setConnectionFactory(connectionFactory)
    initializer.setDatabasePopulator(
      ResourceDatabasePopulator(ClassPathResource("geo-matching-schema.sql"))
    )
    return initializer
  }

  @Bean
  fun geoMatchingR2dbcCustomConversions(): R2dbcCustomConversions = geoMatchingR2dbcConversions()
}
