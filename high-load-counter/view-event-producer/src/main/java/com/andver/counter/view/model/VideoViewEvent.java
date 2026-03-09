package com.andver.counter.view.model;

public record VideoViewEvent(
    long userId,
    long videoId,
    long timestamp
) {

  public VideoViewEvent(long userId, long videoId) {
    this(userId, videoId, System.currentTimeMillis());
  }
}
