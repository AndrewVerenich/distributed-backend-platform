package com.andver.booking.repository

import com.andver.booking.config.BookingConfiguration
import com.andver.booking.config.BookingProperties
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.ServerSocket
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import io.r2dbc.spi.ConnectionFactories
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.r2dbc.core.DatabaseClient

@Tag("integration")
@SpringBootTest(classes = [SeatHoldRaceITApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeatHoldRaceIntegrationTest {

  @Autowired
  lateinit var seats: SeatRepository

  @Autowired
  lateinit var databaseClient: DatabaseClient

  companion object {
    private val port: Int
    private val containerName: String

    init {
      port = ServerSocket(0).use { it.localPort }
      containerName = "booking-pg-it-$port"
      ProcessBuilder(
        "docker", "run", "-d", "--rm",
        "--name", containerName,
        "-p", "$port:5432",
        "-e", "POSTGRES_DB=booking",
        "-e", "POSTGRES_USER=booking",
        "-e", "POSTGRES_PASSWORD=booking",
        "postgres:15-alpine",
      ).inheritIO().start().waitFor(60, TimeUnit.SECONDS)
      waitForPostgres(port)
    }

    @JvmStatic
    @AfterAll
    fun stopPostgres() {
      ProcessBuilder("docker", "rm", "-f", containerName).start().waitFor(30, TimeUnit.SECONDS)
    }

    @JvmStatic
    @DynamicPropertySource
    fun r2dbcProps(registry: DynamicPropertyRegistry) {
      registry.add("spring.r2dbc.url") { "r2dbc:postgresql://booking:booking@127.0.0.1:$port/booking" }
      registry.add("spring.r2dbc.username") { "booking" }
      registry.add("spring.r2dbc.password") { "booking" }
    }

    private fun waitForPostgres(pgPort: Int) {
      val url = "r2dbc:postgresql://booking:booking@127.0.0.1:$pgPort/booking"
      val deadline = System.currentTimeMillis() + 40_000
      while (System.currentTimeMillis() < deadline) {
        try {
          val cf = ConnectionFactories.get(url)
          DatabaseClient.create(cf).sql("SELECT 1").fetch().first().block(Duration.ofSeconds(2))
          return
        } catch (_: Exception) {
          Thread.sleep(500)
        }
      }
      error("Postgres did not become ready on port $pgPort")
    }
  }

  @BeforeEach
  fun resetSeat() {
    databaseClient.sql(
      """
      INSERT INTO seats (id, venue_id, label, row_num, col_num, status)
      VALUES ('race-1', 'race', 'A1', 1, 1, 'free')
      ON CONFLICT (id) DO UPDATE
        SET status = 'free', holder_id = NULL, reservation_id = NULL, hold_until = NULL
      """.trimIndent(),
    ).fetch().rowsUpdated().block()
  }

  @Test
  fun `correct hold race yields exactly one winner`() {
    val wins = AtomicInteger()
    val n = 20
    Flux.range(1, n)
      .flatMap({
        val reservationId = UUID.randomUUID().toString()
        seats.tryHold("race-1", "user-$it", reservationId, Duration.ofSeconds(30))
          .doOnNext { wins.incrementAndGet() }
          .onErrorResume { Mono.empty() }
          .switchIfEmpty(Mono.empty())
      }, n)
      .then()
      .block(Duration.ofSeconds(30))

    assertEquals(1, wins.get(), "exactly one conditional UPDATE should win")
  }

  @Test
  fun `expired hold can be taken by next client`() {
    val first = seats.tryHold("race-1", "a", UUID.randomUUID().toString(), Duration.ofMillis(500))
      .block(Duration.ofSeconds(5))
    requireNotNull(first)

    Thread.sleep(800)

    val second = seats.tryHold("race-1", "b", UUID.randomUUID().toString(), Duration.ofSeconds(30))
      .block(Duration.ofSeconds(5))
    requireNotNull(second)
    assertEquals("b", second.holderId)
  }
}

@SpringBootApplication
@EnableAutoConfiguration
@Import(BookingConfiguration::class, SeatRepository::class)
class SeatHoldRaceITApp
