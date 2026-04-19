package com.andver.saga.model

enum class SagaStatus {
  STARTED,
  EXECUTING,
  COMPENSATING,
  COMPLETED,
  COMPENSATED,
  FAILED
}
