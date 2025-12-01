package com.andver.view.producer;

import com.andver.view.model.ProductViewEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DefaultProductViewProducer implements ProductViewProducer {

  private final static Logger logger = LoggerFactory.getLogger(DefaultProductViewProducer.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final KafkaTemplate<Long, String> kafkaTemplate;
  private final String topicName;

  public DefaultProductViewProducer(
      KafkaTemplate<Long, String> kafkaTemplate,
      @Value("${views.topic}") String topicName
  ) {
    this.kafkaTemplate = kafkaTemplate;
    this.topicName = topicName;
  }

  @Override
  public void sendViewEvent(ProductViewEvent event) {
    try {
      var message = objectMapper.writeValueAsString(event);
      kafkaTemplate.send(topicName, event.userId(), message);
      logger.info("Sent: {}", message);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
