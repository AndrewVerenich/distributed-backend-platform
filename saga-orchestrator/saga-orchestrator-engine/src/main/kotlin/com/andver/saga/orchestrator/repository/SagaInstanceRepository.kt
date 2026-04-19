package com.andver.saga.orchestrator.repository

import com.andver.saga.orchestrator.entity.SagaInstanceEntity
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface SagaInstanceRepository : ReactiveCrudRepository<SagaInstanceEntity, Long> {

  fun findBySagaId(sagaId: UUID): Mono<SagaInstanceEntity>

  fun findByStatus(status: String): Flux<SagaInstanceEntity>

  fun findBySagaType(sagaType: String): Flux<SagaInstanceEntity>

  @Query("SELECT * FROM saga_instance ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
  fun findAllPaged(limit: Int, offset: Int): Flux<SagaInstanceEntity>

  @Query(
    """
    SELECT * FROM saga_instance 
    WHERE (:status IS NULL OR status = :status) 
      AND (:sagaType IS NULL OR saga_type = :sagaType) 
    ORDER BY created_at DESC 
    LIMIT :limit OFFSET :offset
    """
  )
  fun findFiltered(status: String?, sagaType: String?, limit: Int, offset: Int): Flux<SagaInstanceEntity>

  @Query("SELECT COUNT(*) FROM saga_instance WHERE status = :status")
  fun countByStatus(status: String): Mono<Long>
}
