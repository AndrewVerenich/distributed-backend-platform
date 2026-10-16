package com.andver.webhook.worker

import com.andver.webhook.core.RetryPolicy
import io.netty.channel.ChannelOption
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(WorkerProperties::class)
class WorkerConfig {
  @Bean
  fun retryPolicy(props: WorkerProperties): RetryPolicy =
    RetryPolicy(backoffSeconds = props.backoffSeconds, maxAttempts = props.maxAttempts)

  @Bean
  fun outboundWebClient(props: WorkerProperties, builder: WebClient.Builder): WebClient {
    val httpClient = HttpClient.create()
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (props.httpTimeoutSeconds * 1000).toInt())
      .responseTimeout(Duration.ofSeconds(props.httpTimeoutSeconds))
    return builder.clientConnector(ReactorClientHttpConnector(httpClient)).build()
  }
}
