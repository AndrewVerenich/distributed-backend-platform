package com.andver.saga.participant.consumer

import com.andver.saga.model.ReplyStatus
import com.andver.saga.model.SagaCommand
import com.andver.saga.model.SagaReply
import com.andver.saga.participant.handler.SagaCommandHandler
import com.andver.saga.participant.producer.SagaReplyProducer
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener

class SagaCommandConsumer(
  private val handlers: Map<String, SagaCommandHandler>,
  private val replyProducer: SagaReplyProducer
) {
  private val log = LoggerFactory.getLogger(SagaCommandConsumer::class.java)

  @KafkaListener(
    topics = ["\${saga.participant.command-topic}"],
    groupId = "\${spring.application.name}"
  )
  fun onCommand(command: SagaCommand) {
    log.info("Received command: sagaId={} step={} compensation={}",
      command.sagaId, command.stepName, command.isCompensation)

    val handler = handlers.values.firstOrNull()
    if (handler == null) {
      log.error("No handler found for command step={}", command.stepName)
      val reply = SagaReply(
        sagaId = command.sagaId,
        stepName = command.stepName,
        status = ReplyStatus.FAILURE,
        errorMessage = "No handler registered",
        isCompensation = command.isCompensation
      )
      replyProducer.sendReply(reply)
      return
    }

    try {
      val reply = if (command.isCompensation) {
        handler.compensate(command).block()
      } else {
        handler.handle(command).block()
      }
      if (reply != null) {
        replyProducer.sendReply(reply)
      }
    } catch (e: Exception) {
      log.error("Handler failed for step={}: {}", command.stepName, e.message, e)
      val reply = SagaReply(
        sagaId = command.sagaId,
        stepName = command.stepName,
        status = ReplyStatus.FAILURE,
        errorMessage = e.message,
        isCompensation = command.isCompensation
      )
      replyProducer.sendReply(reply)
    }
  }
}
