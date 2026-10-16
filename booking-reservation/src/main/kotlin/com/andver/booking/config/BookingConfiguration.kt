package com.andver.booking.config

import io.r2dbc.spi.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator

@Configuration
class BookingConfiguration {

  @Bean
  fun bookingSchema(connectionFactory: ConnectionFactory): ConnectionFactoryInitializer {
    val initializer = ConnectionFactoryInitializer()
    initializer.setConnectionFactory(connectionFactory)
    initializer.setDatabasePopulator(
      ResourceDatabasePopulator(ClassPathResource("booking-schema.sql")),
    )
    return initializer
  }

  @Bean
  fun transactionManager(connectionFactory: ConnectionFactory): ReactiveTransactionManager =
    R2dbcTransactionManager(connectionFactory)

  @Bean
  fun transactionalOperator(tm: ReactiveTransactionManager): TransactionalOperator =
    TransactionalOperator.create(tm)
}
