package com.andver.cache.demo.repo

import com.andver.cache.demo.model.CatalogItem
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Component
class SlowCatalogRepository {
  private val data = ConcurrentHashMap<String, CatalogItem>()

  init {
    repeat(100_000) { i ->
      val id = "sku-$i"
      data[id] = CatalogItem(
        id = id,
        title = "Product #$i",
        price = Random(i).nextDouble(5.0, 500.0),
      )
    }
  }

  fun findById(id: String): CatalogItem? {
    Thread.sleep(Random.nextLong(20, 50))
    return data[id]
  }

  fun knownKeys(): Set<String> = data.keys
}
