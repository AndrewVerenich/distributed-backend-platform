package com.andver.idempotency.client

import org.springframework.web.reactive.function.client.WebClient

fun WebClient.RequestHeadersSpec<*>.idempotent(
  operationId: String,
  owner: String,
): WebClient.RequestHeadersSpec<*> =
  attribute(IdempotencyClientAttributes.OPERATION_ID, operationId)
    .attribute(IdempotencyClientAttributes.OWNER, owner)

fun WebClient.RequestBodySpec.idempotent(
  operationId: String,
  owner: String,
): WebClient.RequestBodySpec =
  attribute(IdempotencyClientAttributes.OPERATION_ID, operationId)
    .attribute(IdempotencyClientAttributes.OWNER, owner) as WebClient.RequestBodySpec
