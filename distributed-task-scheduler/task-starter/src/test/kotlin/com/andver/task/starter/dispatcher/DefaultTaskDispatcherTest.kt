package com.andver.task.starter.dispatcher

import com.andver.task.starter.handler.TaskExecutionHandler
import com.andver.task.starter.model.RunTaskParams
import com.andver.task.starter.model.Task
import com.andver.task.starter.model.TaskStatus
import com.andver.task.starter.producer.TaskStatusProducer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import java.util.*

class DefaultTaskDispatcherTest {

  private val statusProducer = mockk<TaskStatusProducer>(relaxed = true)
  private lateinit var dispatcher: DefaultTaskDispatcher

  private val dailyReportTask = buildTask("daily-report")
  private val cleanupTask = buildTask("cleanup")

  private val dailyReportHandler = mockk<TaskExecutionHandler>()
  private val cleanupHandler = mockk<TaskExecutionHandler>()

  @BeforeEach
  fun setUp() {
    every { dailyReportHandler.task } returns dailyReportTask
    every { cleanupHandler.task } returns cleanupTask

    dispatcher = DefaultTaskDispatcher(
      handlers = listOf(dailyReportHandler, cleanupHandler),
      taskStatusProducer = statusProducer,
    )
  }

  @Test
  fun `dispatch finds the correct handler and executes it`() {
    val params = RunTaskParams(uuid = UUID.randomUUID().toString(), name = "daily-report", params = emptyMap())
    every { dailyReportHandler.executeTaskAsync(params) } returns Mono.empty()

    StepVerifier.create(dispatcher.dispatch(params))
      .verifyComplete()

    verify { dailyReportHandler.executeTaskAsync(params) }
  }

  @Test
  fun `dispatch routes to the correct handler among multiple registered handlers`() {
    val params = RunTaskParams(uuid = UUID.randomUUID().toString(), name = "cleanup", params = emptyMap())
    every { cleanupHandler.executeTaskAsync(params) } returns Mono.empty()

    StepVerifier.create(dispatcher.dispatch(params))
      .verifyComplete()

    verify(exactly = 0) { dailyReportHandler.executeTaskAsync(any()) }
    verify { cleanupHandler.executeTaskAsync(params) }
  }

  @Test
  fun `dispatch sends ERROR status and completes when task name is unknown`() {
    val params = RunTaskParams(uuid = "unknown-uuid", name = "non-existent-task", params = emptyMap())

    StepVerifier.create(dispatcher.dispatch(params))
      .verifyComplete()

    verify { statusProducer.sendStatus(TaskStatus.ERROR, "unknown-uuid") }
    verify(exactly = 0) { dailyReportHandler.executeTaskAsync(any()) }
    verify(exactly = 0) { cleanupHandler.executeTaskAsync(any()) }
  }

  @Test
  fun `dispatch passes task params through to the handler`() {
    val taskParams = mapOf("userId" to 42, "batchSize" to 100)
    val params = RunTaskParams(uuid = "test-uuid", name = "daily-report", params = taskParams)
    every { dailyReportHandler.executeTaskAsync(params) } returns Mono.empty()

    StepVerifier.create(dispatcher.dispatch(params))
      .verifyComplete()

    verify { dailyReportHandler.executeTaskAsync(match { it.params == taskParams }) }
  }

  @Test
  fun `dispatch propagates error emitted by the handler`() {
    val params = RunTaskParams(uuid = "err-uuid", name = "daily-report", params = emptyMap())
    every { dailyReportHandler.executeTaskAsync(params) } returns
        Mono.error(RuntimeException("Handler failure"))

    StepVerifier.create(dispatcher.dispatch(params))
      .expectError(RuntimeException::class.java)
      .verify()
  }

  @Test
  fun `dispatcher with no handlers always sends ERROR for any task name`() {
    val emptyDispatcher = DefaultTaskDispatcher(emptyList(), statusProducer)
    val params = RunTaskParams(uuid = "any-uuid", name = "any-task", params = emptyMap())

    StepVerifier.create(emptyDispatcher.dispatch(params))
      .verifyComplete()

    verify { statusProducer.sendStatus(TaskStatus.ERROR, "any-uuid") }
  }

  private fun buildTask(name: String) = object : Task {
    override val taskName = name
    override val scheduler = Schedulers.immediate()
    override fun execute(param: Map<String, Any>): Mono<Void> = Mono.empty()
  }
}
