package com.andver.saga.orchestrator.repository

import com.andver.saga.orchestrator.entity.SagaStepEntity
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface SagaStepRepository : ReactiveCrudRepository<SagaStepEntity, Long> {

  fun findBySagaInstanceIdOrderByStepOrder(sagaInstanceId: Long): Flux<SagaStepEntity>

  fun findBySagaInstanceIdAndStepName(sagaInstanceId: Long, stepName: String): Mono<SagaStepEntity>

  @Query(
    """
    SELECT ss.* FROM saga_step ss 
    JOIN saga_instance si ON ss.saga_instance_id = si.id 
    WHERE ss.status = 'EXECUTING' 
      AND ss.started_at < NOW() - INTERVAL '1 second' * :timeoutSeconds
    """
  )
  fun findTimedOutSteps(timeoutSeconds: Int): Flux<SagaStepEntity>
}
