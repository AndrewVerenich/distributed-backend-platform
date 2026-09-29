package com.andver.geomatching.store

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ClassPathResource
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.net.ServerSocket
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactories
import org.springframework.r2dbc.core.DatabaseClient

@Tag("integration")
@SpringBootTest(classes = [ProviderRepositoryITApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProviderRepositoryIntegrationTest {

  @Autowired
  lateinit var providers: ProviderRepository

  companion object {
    private val port: Int
    private val containerName: String

    init {
      port = ServerSocket(0).use { it.localPort }
      containerName = "geo-pg-it-$port"
      ProcessBuilder(
        "docker", "run", "-d", "--rm",
        "--name", containerName,
        "-p", "$port:5432",
        "-e", "POSTGRES_DB=geo",
        "-e", "POSTGRES_USER=geo",
        "-e", "POSTGRES_PASSWORD=geo",
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
      registry.add("spring.r2dbc.url") { "r2dbc:postgresql://geo:geo@127.0.0.1:$port/geo" }
      registry.add("spring.r2dbc.username") { "geo" }
      registry.add("spring.r2dbc.password") { "geo" }
    }

    private fun waitForPostgres(pgPort: Int) {
      val url = "r2dbc:postgresql://geo:geo@127.0.0.1:$pgPort/geo"
      val deadline = System.currentTimeMillis() + 40_000
      var last: Exception? = null
      while (System.currentTimeMillis() < deadline) {
        try {
          val cf = ConnectionFactories.get(url)
          DatabaseClient.create(cf).sql("SELECT 1").fetch().first().block(Duration.ofSeconds(2))
          return
        } catch (ex: Exception) {
          last = ex
          Thread.sleep(400)
        }
      }
      throw IllegalStateException("Postgres did not become ready", last)
    }
  }

  @BeforeEach
  fun clean() {
    providers.deleteAll().block()
  }

  @Test
  fun `two concurrent claims on one free provider yield a single winner`() {
    val now = Instant.parse("2026-01-01T12:00:00Z")
    providers.upsertLocation("p1", 27.56, 53.90, now).block()

    val first = providers.tryClaim("p1", "r-a", now.minusSeconds(15)).hasElement()
      .subscribeOn(Schedulers.parallel())
    val second = providers.tryClaim("p1", "r-b", now.minusSeconds(15)).hasElement()
      .subscribeOn(Schedulers.parallel())
    val results = Mono.zip(first, second).block()!!

    val wins = listOf(results.t1, results.t2).count { it }
    assertEquals(1, wins)
    val winner = providers.findById("p1").block()!!
    assertEquals("busy", winner.status.db())
  }
}

@SpringBootApplication(
  exclude = [
    RedisAutoConfiguration::class,
    RedisReactiveAutoConfiguration::class,
  ],
)
@EnableR2dbcRepositories(basePackageClasses = [ProviderRepository::class])
class ProviderRepositoryITApp {

  @Bean
  fun geoMatchingR2dbcCustomConversions(): R2dbcCustomConversions = geoMatchingR2dbcConversions()

  @Bean
  fun geoMatchingSchema(connectionFactory: ConnectionFactory): ConnectionFactoryInitializer {
    val initializer = ConnectionFactoryInitializer()
    initializer.setConnectionFactory(connectionFactory)
    initializer.setDatabasePopulator(
      ResourceDatabasePopulator(ClassPathResource("geo-matching-schema.sql"))
    )
    return initializer
  }
}
