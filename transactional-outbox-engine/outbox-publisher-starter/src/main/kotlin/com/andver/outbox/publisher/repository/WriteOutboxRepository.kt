package com.andver.outbox.publisher.repository

import com.andver.outbox.publisher.model.OutboxEvent
import org.springframework.data.repository.reactive.ReactiveCrudRepository

interface WriteOutboxRepository : ReactiveCrudRepository<OutboxEvent, Long>