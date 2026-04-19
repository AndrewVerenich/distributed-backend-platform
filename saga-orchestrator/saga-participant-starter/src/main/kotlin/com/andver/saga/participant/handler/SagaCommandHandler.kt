package com.andver.saga.participant.handler

import com.andver.saga.model.SagaCommand
import com.andver.saga.model.SagaReply
import reactor.core.publisher.Mono

interface SagaCommandHandler {
  val commandType: String

  fun handle(command: SagaCommand): Mono<SagaReply>

  fun compensate(command: SagaCommand): Mono<SagaReply> =
    Mono.error(UnsupportedOperationException("Compensation not supported for commandType=$commandType"))
}
