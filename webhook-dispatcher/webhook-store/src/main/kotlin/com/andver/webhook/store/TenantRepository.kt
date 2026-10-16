package com.andver.webhook.store

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
class TenantRepository(
  private val db: DatabaseClient,
) {
  fun findByApiKey(apiKey: String): Mono<Tenant> =
    db.sql("SELECT id, api_key, name FROM tenants WHERE api_key = :apiKey")
      .bind("apiKey", apiKey)
      .map { row, _ ->
        Tenant(
          id = row.get("id", String::class.java)!!,
          apiKey = row.get("api_key", String::class.java)!!,
          name = row.get("name", String::class.java)!!,
        )
      }
      .one()
}
