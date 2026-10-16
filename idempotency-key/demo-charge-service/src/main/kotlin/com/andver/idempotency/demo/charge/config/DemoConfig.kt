package com.andver.idempotency.demo.charge.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator

@Configuration
class DemoConfig {
  @Bean
  fun transactionalOperator(transactionManager: ReactiveTransactionManager): TransactionalOperator =
    TransactionalOperator.create(transactionManager)
}
