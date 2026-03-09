package com.andver.counter.view.producer;

import com.andver.counter.view.model.VideoViewEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DefaultVideoViewProducer implements VideoViewProducer {

  private static final Logger logger = LoggerFactory.getLogger(DefaultVideoViewProducer.class);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final KafkaTemplate<Long, String> kafkaTemplate;
  private final String topicName;

  public DefaultVideoViewProducer(
      KafkaTemplate<Long, String> kafkaTemplate,
      @Value("${views.topic}") String topicName
  ) {
    this.kafkaTemplate = kafkaTemplate;
    this.topicName = topicName;
  }

  @Override
  public void send(VideoViewEvent event) {
    try {
      String message = objectMapper.writeValueAsString(event);
      kafkaTemplate.send(topicName, event.videoId(), message);
      logger.info("Sent view event: userId={} videoId={}", event.userId(), event.videoId());
    } catch (Exception e) {
      logger.error("Failed to send view event", e);
    }
  }
}
