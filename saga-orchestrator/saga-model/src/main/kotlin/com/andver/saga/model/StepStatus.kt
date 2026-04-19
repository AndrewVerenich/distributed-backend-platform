package com.andver.saga.model

enum class StepStatus {
  PENDING,
  EXECUTING,
  COMPLETED,
  FAILED,
  COMPENSATING,
  COMPENSATED,
  SKIPPED
}
