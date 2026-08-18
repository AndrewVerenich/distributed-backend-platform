package com.andver.cache.autoconfigure

import com.andver.cache.api.BoundedCacheRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@EnableConfigurationProperties(CacheEvictionProperties::class)
class CacheEvictionAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  fun cacheConfigBuilder(properties: CacheEvictionProperties): CacheConfigBuilder =
    CacheConfigBuilder(properties)

  @Bean
  @ConditionalOnMissingBean
  fun boundedCacheRegistry(cacheConfigBuilder: CacheConfigBuilder): BoundedCacheRegistry =
    DefaultBoundedCacheRegistry(cacheConfigBuilder)
}
