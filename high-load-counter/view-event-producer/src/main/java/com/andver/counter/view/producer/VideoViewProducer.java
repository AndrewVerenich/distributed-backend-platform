package com.andver.counter.view.producer;

import com.andver.counter.view.model.VideoViewEvent;

public interface VideoViewProducer {

  void send(VideoViewEvent event);
}
