package com.andver.saga.orchestrator.kafka

import com.andver.saga.model.SagaReply
import com.andver.saga.orchestrator.engine.SagaOrchestrator
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class SagaReplyConsumer(
  private val sagaOrchestrator: SagaOrchestrator
) {
  private val log = LoggerFactory.getLogger(SagaReplyConsumer::class.java)

  @KafkaListener(topics = ["saga.reply"], groupId = "saga-orchestrator")
  fun onReply(reply: SagaReply) {
    log.info("Received reply: sagaId={} step={} status={}", reply.sagaId, reply.stepName, reply.status)
    sagaOrchestrator.handleReply(reply).block()
  }
}
