package com.andver.counter.repository

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@Repository
class VideoCounterRepository(
  private val db: DatabaseClient,
) {

  fun upsert(videoId: Long, totalViews: Long, uniqueViewers: Long): Mono<Void> {
    return db.sql(
      """
      INSERT INTO video_view_counts (video_id, total_views, unique_viewers_estimate, last_updated)
      VALUES (:videoId, :totalViews, :uniqueViewers, :now)
      ON CONFLICT (video_id) DO UPDATE SET
        total_views = EXCLUDED.total_views,
        unique_viewers_estimate = EXCLUDED.unique_viewers_estimate,
        last_updated = EXCLUDED.last_updated
      """.trimIndent()
    )
      .bind("videoId", videoId)
      .bind("totalViews", totalViews)
      .bind("uniqueViewers", uniqueViewers)
      .bind("now", LocalDateTime.now())
      .then()
  }
}
