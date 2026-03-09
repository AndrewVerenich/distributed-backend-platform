package com.andver.counter.view.emulator;

import com.andver.counter.view.model.VideoViewEvent;
import com.andver.counter.view.producer.VideoViewProducer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class VideoViewEmulator implements CommandLineRunner {

  private final VideoViewProducer producer;
  private final long userCount;
  private final long videoCount;
  private final long intervalMs;
  private final Random random = new Random();

  public VideoViewEmulator(
      VideoViewProducer producer,
      @Value("${views.user-count}") long userCount,
      @Value("${views.video-count}") long videoCount,
      @Value("${views.interval-ms}") long intervalMs
  ) {
    this.producer = producer;
    this.userCount = userCount;
    this.videoCount = videoCount;
    this.intervalMs = intervalMs;
  }

  @Override
  public void run(String... args) throws Exception {
    List<Long> userIds = new ArrayList<>();
    for (long i = 1; i <= userCount; i++) {
      userIds.add(i);
    }

    List<Long> videoIds = new ArrayList<>();
    for (long i = 1; i <= videoCount; i++) {
      videoIds.add(i);
    }

    ExecutorService executor = Executors.newFixedThreadPool(userIds.size());

    for (Long userId : userIds) {
      executor.submit(() -> {
        while (true) {
          long videoId = selectVideoId(videoIds);
          producer.send(new VideoViewEvent(userId, videoId));

          try {
            Thread.sleep(intervalMs);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
        }
      });
    }
  }

  private long selectVideoId(List<Long> videoIds) {
    // Top 20% of videos receive ~80% of traffic
    if (random.nextDouble() < 0.8) {
      int topCount = Math.max(1, (int) (videoIds.size() * 0.2));
      return videoIds.get(random.nextInt(topCount));
    }
    return videoIds.get(random.nextInt(videoIds.size()));
  }
}
