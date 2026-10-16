package com.andver.idempotency.client.store

fun interface OperationKeyStore {
  fun resolve(operationId: String): String
}
