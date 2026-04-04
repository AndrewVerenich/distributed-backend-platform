package com.andver.sharding

import com.andver.sharding.properties.ShardingProperties
import com.andver.sharding.registry.ShardRegistry
import com.andver.sharding.resolver.ShardResolver
import com.andver.sharding.routing.ShardRoutingConnectionFactory
import com.andver.sharding.scatter.ScatterGatherTemplate
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@AutoConfiguration
@EnableConfigurationProperties(ShardingProperties::class)
class ShardingAutoConfiguration(
  private val properties: ShardingProperties,
) {

  @Bean
  fun shardResolver(): ShardResolver {
    val shardNames = properties.shards.map { it.name }
    val defaultShard = properties.defaultShard ?: shardNames.firstOrNull()
    ?: error("sharding.defaultShard is not set and sharding.shards is empty")
    return ShardResolver(shardNames = shardNames, defaultShard = defaultShard)
  }

  @Bean
  fun shardRegistry(): ShardRegistry {
    val shardNames = properties.shards.map { it.name }
    val defaultShard = properties.defaultShard ?: shardNames.firstOrNull()
    ?: error("sharding.defaultShard is not set and sharding.shards is empty")

    return ShardRegistry(
      shardNames = shardNames,
      defaultShard = defaultShard,
      connectionFactories = shardConnectionFactories(),
    )
  }

  private fun shardConnectionFactories(): Map<String, ConnectionFactory> {
    return properties.shards.associate { shard ->
      val options =
        ConnectionFactoryOptions
          .builder()
          .option(ConnectionFactoryOptions.DRIVER, "postgresql")
          .option(ConnectionFactoryOptions.HOST, shard.host)
          .option(ConnectionFactoryOptions.PORT, shard.port)
          .option(ConnectionFactoryOptions.DATABASE, shard.database)
          .option(ConnectionFactoryOptions.USER, shard.username)
          .option(ConnectionFactoryOptions.PASSWORD, shard.password)
          .build()
      shard.name to ConnectionFactories.get(options)
    }
  }

  @Bean
  @Primary
  fun r2dbcConnectionFactory(shardRegistry: ShardRegistry): ConnectionFactory {
    val routingConnectionFactory = ShardRoutingConnectionFactory(shardRegistry.defaultShardName())
    routingConnectionFactory.setTargetConnectionFactories(
      shardRegistry
        .let { registry ->
          registry.allShardNames().associateWith { shardName -> registry.connectionFactory(shardName) }
        },
    )
    routingConnectionFactory.setDefaultTargetConnectionFactory(
      shardRegistry.connectionFactory(shardRegistry.defaultShardName()),
    )
    return routingConnectionFactory
  }

  @Bean
  fun scatterGatherTemplate(shardRegistry: ShardRegistry): ScatterGatherTemplate {
    return ScatterGatherTemplate(shardRegistry)
  }
}

