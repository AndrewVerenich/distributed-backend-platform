package com.andver.task.starter.handler

import com.andver.task.starter.model.RunTaskParams
import com.andver.task.starter.model.Task
import com.andver.task.starter.model.TaskStatus
import com.andver.task.starter.producer.TaskStatusProducer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.redisson.api.RLockReactive
import org.redisson.api.RedissonReactiveClient
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import java.util.*
import java.util.concurrent.TimeUnit

class DefaultTaskExecutionHandlerTest {

  private val statusProducer = mockk<TaskStatusProducer>(relaxed = true)
  private val redissonClient = mockk<RedissonReactiveClient>()
  private val lock = mockk<RLockReactive>()

  private val params = RunTaskParams(
    uuid = UUID.randomUUID().toString(),
    name = "cleanup",
    params = emptyMap(),
  )

  @BeforeEach
  fun setUp() {
    every { redissonClient.getLock("task:cleanup") } returns lock
  }

  @Test
  fun `executeTaskAsync returns empty Mono immediately (fire-and-forget)`() {
    val task = buildTask("cleanup", Mono.empty())
    val handler = DefaultTaskExecutionHandler(task, statusProducer, redissonClient)

    every { lock.tryLock(any(), any(), TimeUnit.SECONDS) } returns Mono.just(true)
    every { lock.forceUnlock() } returns Mono.just(true)

    StepVerifier.create(handler.executeTaskAsync(params))
      .verifyComplete()
  }

  @Test
  fun `executeTaskAsync sends IN_PROGRESS and FINISHED statuses when lock is acquired`() {
    val task = buildTask("cleanup", Mono.empty())
    val handler = DefaultTaskExecutionHandler(task, statusProducer, redissonClient)

    every { lock.tryLock(any(), any(), TimeUnit.SECONDS) } returns Mono.just(true)
    every { lock.forceUnlock() } returns Mono.just(true)

    handler.executeTaskAsync(params).block()
    Thread.sleep(300)

    verify { statusProducer.sendStatus(TaskStatus.IN_PROGRESS, params.uuid) }
    verify { statusProducer.sendStatus(TaskStatus.FINISHED, params.uuid) }
  }

  @Test
  fun `executeTaskAsync sends SKIPPED status when lock cannot be acquired`() {
    val task = buildTask("cleanup", Mono.empty())
    val handler = DefaultTaskExecutionHandler(task, statusProducer, redissonClient)

    every { lock.tryLock(any(), any(), TimeUnit.SECONDS) } returns Mono.just(false)

    handler.executeTaskAsync(params).block()
    Thread.sleep(300)

    verify { statusProducer.sendStatus(TaskStatus.SKIPPED, params.uuid) }
    verify(exactly = 0) { statusProducer.sendStatus(TaskStatus.IN_PROGRESS, any()) }
  }

  @Test
  fun `executeTaskAsync sends ERROR status when task execution throws`() {
    val failingTask = buildTask("cleanup", Mono.error(RuntimeException("DB down")))
    val handler = DefaultTaskExecutionHandler(failingTask, statusProducer, redissonClient)

    every { lock.tryLock(any(), any(), TimeUnit.SECONDS) } returns Mono.just(true)
    every { lock.forceUnlock() } returns Mono.just(true)

    handler.executeTaskAsync(params).block()
    Thread.sleep(300)

    verify { statusProducer.sendStatus(TaskStatus.ERROR, params.uuid) }
    verify(exactly = 0) { statusProducer.sendStatus(TaskStatus.FINISHED, any()) }
  }

  @Test
  fun `task property returns the provided task instance`() {
    val task = buildTask("cleanup", Mono.empty())
    val handler = DefaultTaskExecutionHandler(task, statusProducer, redissonClient)

    assert(handler.task === task)
  }

  private fun buildTask(name: String, result: Mono<Void>) = object : Task {
    override val taskName = name
    override val scheduler = Schedulers.immediate()
    override fun execute(param: Map<String, Any>): Mono<Void> = result
  }
}
