package com.andver.idempotency.api

enum class IdempotencyStatus {
  STARTED,
  COMPLETED,
  ;

  fun dbValue(): String = name.lowercase()

  companion object {
    fun fromDb(value: String): IdempotencyStatus =
      entries.first { it.dbValue() == value.lowercase() }
  }
}
