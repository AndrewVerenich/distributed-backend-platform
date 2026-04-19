package com.andver.saga.orchestrator.engine

import com.andver.saga.model.*
import com.andver.saga.orchestrator.dsl.SagaDefinition
import com.andver.saga.orchestrator.dsl.SagaRegistry
import com.andver.saga.orchestrator.dsl.StepDefinition
import com.andver.saga.orchestrator.entity.SagaInstanceEntity
import com.andver.saga.orchestrator.entity.SagaStepEntity
import com.andver.saga.orchestrator.kafka.SagaCommandProducer
import com.andver.saga.orchestrator.metrics.SagaMetrics
import com.andver.saga.orchestrator.repository.SagaInstanceRepository
import com.andver.saga.orchestrator.repository.SagaStepRepository
import com.andver.saga.orchestrator.sse.SagaEventPublisher
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

@Service
class SagaOrchestrator(
  private val sagaRegistry: SagaRegistry,
  private val sagaStateMachine: SagaStateMachine,
  private val instanceRepository: SagaInstanceRepository,
  private val stepRepository: SagaStepRepository,
  private val commandProducer: SagaCommandProducer,
  private val objectMapper: ObjectMapper,
  private val sagaMetrics: SagaMetrics,
  private val eventPublisher: SagaEventPublisher
) {
  private val log = LoggerFactory.getLogger(SagaOrchestrator::class.java)

  fun startSaga(sagaType: String, payload: String): Mono<SagaInstanceEntity> {
    val definition = sagaRegistry.get(sagaType)
    val sagaId = UUID.randomUUID()

    log.info("Starting saga [{}] id={}", sagaType, sagaId)

    val instance = SagaInstanceEntity(
      sagaId = sagaId,
      sagaType = sagaType,
      status = SagaStatus.STARTED.name,
      currentStep = definition.steps.first().stepName,
      payload = payload
    )

    return instanceRepository.save(instance)
      .flatMap { saved ->
        createStepRecords(saved, definition)
          .then(Mono.just(saved))
      }
      .flatMap { saved ->
        sagaMetrics.recordSagaStarted(sagaType)
        eventPublisher.publishSagaEvent(saved)
        executeStep(saved, definition, definition.steps.first())
          .thenReturn(saved)
      }
  }

  fun handleReply(reply: SagaReply): Mono<Void> {
    log.info("Received reply for saga={} step={} status={} compensation={}",
      reply.sagaId, reply.stepName, reply.status, reply.isCompensation)

    return instanceRepository.findBySagaId(reply.sagaId)
      .flatMap { instance ->
        val definition = sagaRegistry.get(instance.sagaType)
        stepRepository.findBySagaInstanceIdAndStepName(instance.id!!, reply.stepName)
          .flatMap { step ->
            updateStepFromReply(step, reply, definition)
              .flatMap { updatedStep ->
                eventPublisher.publishStepEvent(instance, updatedStep)
                processNextAction(instance, definition, reply)
              }
          }
      }
  }

  fun retryStep(instanceId: Long, stepName: String): Mono<Void> {
    return instanceRepository.findById(instanceId)
      .flatMap { instance ->
        val definition = sagaRegistry.get(instance.sagaType)
        val stepDef = definition.steps.first { it.stepName == stepName }
        stepRepository.findBySagaInstanceIdAndStepName(instance.id!!, stepName)
          .flatMap { step ->
            val updated = step.copy(
              status = StepStatus.EXECUTING.name,
              retryCount = step.retryCount + 1,
              startedAt = LocalDateTime.now(),
              errorMessage = null
            )
            stepRepository.save(updated)
              .flatMap {
                executeStep(instance, definition, stepDef)
              }
          }
      }
  }

  private fun createStepRecords(
    instance: SagaInstanceEntity,
    definition: SagaDefinition<out Any>
  ): Mono<Void> {
    val steps = definition.steps.mapIndexed { idx, stepDef ->
      SagaStepEntity(
        sagaInstanceId = instance.id!!,
        stepName = stepDef.stepName,
        stepType = stepDef.stepType.name,
        stepOrder = idx,
        status = StepStatus.PENDING.name
      )
    }
    return stepRepository.saveAll(steps).then()
  }

  @Suppress("UNCHECKED_CAST")
  private fun executeStep(
    instance: SagaInstanceEntity,
    definition: SagaDefinition<out Any>,
    stepDef: StepDefinition<out Any>
  ): Mono<Void> {
    val data = objectMapper.readValue(instance.payload, definition.dataClass)
    val rawDef = stepDef as StepDefinition<Any>
    val command = rawDef.commandBuilder(data)
    val commandJson = objectMapper.writeValueAsString(command)

    val sagaCommand = SagaCommand(
      sagaId = instance.sagaId,
      sagaType = instance.sagaType,
      stepName = stepDef.stepName,
      payload = commandJson,
      isCompensation = false
    )

    return stepRepository.findBySagaInstanceIdAndStepName(instance.id!!, stepDef.stepName)
      .flatMap { step ->
        val updated = step.copy(
          status = StepStatus.EXECUTING.name,
          commandPayload = commandJson,
          startedAt = LocalDateTime.now()
        )
        stepRepository.save(updated)
      }
      .flatMap {
        val updatedInstance = instance.copy(
          currentStep = stepDef.stepName,
          status = SagaStatus.EXECUTING.name,
          updatedAt = LocalDateTime.now()
        )
        instanceRepository.save(updatedInstance)
      }
      .flatMap {
        commandProducer.sendCommand(stepDef.participant, sagaCommand)
      }
  }

  @Suppress("UNCHECKED_CAST")
  private fun executeCompensation(
    instance: SagaInstanceEntity,
    definition: SagaDefinition<out Any>,
    stepDef: StepDefinition<out Any>
  ): Mono<Void> {
    val data = objectMapper.readValue(instance.payload, definition.dataClass)
    val rawDef = stepDef as StepDefinition<Any>
    val compensationCommand = rawDef.compensationBuilder?.invoke(data)
      ?: return Mono.empty()
    val commandJson = objectMapper.writeValueAsString(compensationCommand)

    val sagaCommand = SagaCommand(
      sagaId = instance.sagaId,
      sagaType = instance.sagaType,
      stepName = stepDef.stepName,
      payload = commandJson,
      isCompensation = true
    )

    return stepRepository.findBySagaInstanceIdAndStepName(instance.id!!, stepDef.stepName)
      .flatMap { step ->
        val updated = step.copy(
          status = StepStatus.COMPENSATING.name,
          startedAt = LocalDateTime.now()
        )
        stepRepository.save(updated)
      }
      .flatMap {
        val updatedInstance = instance.copy(
          currentStep = stepDef.stepName,
          status = SagaStatus.COMPENSATING.name,
          updatedAt = LocalDateTime.now()
        )
        instanceRepository.save(updatedInstance)
      }
      .flatMap {
        sagaMetrics.recordCompensationTriggered(instance.sagaType)
        commandProducer.sendCommand(stepDef.participant, sagaCommand)
      }
  }

  @Suppress("UNCHECKED_CAST")
  private fun updateStepFromReply(
    step: SagaStepEntity,
    reply: SagaReply,
    definition: SagaDefinition<out Any>
  ): Mono<SagaStepEntity> {
    val newStatus = if (reply.isCompensation) {
      if (reply.status == ReplyStatus.SUCCESS) StepStatus.COMPENSATED else StepStatus.FAILED
    } else {
      if (reply.status == ReplyStatus.SUCCESS) StepStatus.COMPLETED else StepStatus.FAILED
    }

    val updated = step.copy(
      status = newStatus.name,
      replyPayload = reply.payload,
      errorMessage = reply.errorMessage,
      completedAt = LocalDateTime.now()
    )

    sagaMetrics.recordStepDuration(definition.steps.first().stepName, step)

    return stepRepository.save(updated)
  }

  @Suppress("UNCHECKED_CAST")
  private fun processNextAction(
    instance: SagaInstanceEntity,
    definition: SagaDefinition<out Any>,
    reply: SagaReply
  ): Mono<Void> {
    return stepRepository.findBySagaInstanceIdOrderByStepOrder(instance.id!!)
      .collectList()
      .flatMap { steps ->
        // If reply was successful and had onReply handler, update saga payload
        val updatedPayloadMono = if (reply.status == ReplyStatus.SUCCESS && !reply.isCompensation) {
          updateSagaPayload(instance, definition, reply)
        } else {
          Mono.just(instance)
        }

        updatedPayloadMono.flatMap { updatedInstance ->
          val action = sagaStateMachine.determineNextAction(
            definition, steps, reply.stepName,
            reply.status == ReplyStatus.SUCCESS,
            reply.isCompensation
          )

          when (action) {
            is SagaAction.ExecuteStep -> {
              val nextDef = definition.steps.first { it.stepName == action.stepName }
              executeStep(updatedInstance, definition, nextDef)
            }

            is SagaAction.CompensateStep -> {
              val compDef = definition.steps.first { it.stepName == action.stepName }
              executeCompensation(updatedInstance, definition, compDef)
            }

            is SagaAction.RetryStep -> {
              retryStep(updatedInstance.id!!, action.stepName)
            }

            is SagaAction.Complete -> {
              completeSaga(updatedInstance, action.status)
            }
          }
        }
      }
  }

  @Suppress("UNCHECKED_CAST")
  private fun updateSagaPayload(
    instance: SagaInstanceEntity,
    definition: SagaDefinition<out Any>,
    reply: SagaReply
  ): Mono<SagaInstanceEntity> {
    val stepDef = definition.steps.firstOrNull { it.stepName == reply.stepName }
      ?: return Mono.just(instance)

    val rawDef = stepDef as StepDefinition<Any>
    val handler = rawDef.onReplyHandler ?: return Mono.just(instance)

    val data = objectMapper.readValue(instance.payload, definition.dataClass)
    val replyNode: JsonNode = reply.payload?.let { objectMapper.readTree(it) }
      ?: objectMapper.createObjectNode()
    val updatedData = handler(data, replyNode)
    val updatedPayload = objectMapper.writeValueAsString(updatedData)

    val updated = instance.copy(payload = updatedPayload, updatedAt = LocalDateTime.now())
    return instanceRepository.save(updated)
  }

  private fun completeSaga(instance: SagaInstanceEntity, status: SagaStatus): Mono<Void> {
    log.info("Saga [{}] id={} completed with status={}", instance.sagaType, instance.sagaId, status)

    val updated = instance.copy(
      status = status.name,
      updatedAt = LocalDateTime.now(),
      completedAt = LocalDateTime.now()
    )

    return instanceRepository.save(updated)
      .doOnSuccess {
        sagaMetrics.recordSagaCompleted(instance.sagaType, status)
        eventPublisher.publishSagaEvent(it)
      }
      .then()
  }
}
