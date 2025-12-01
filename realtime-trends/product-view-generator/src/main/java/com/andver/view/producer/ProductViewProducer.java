package com.andver.view.producer;

import com.andver.view.model.ProductViewEvent;

public interface ProductViewProducer {

  void sendViewEvent(ProductViewEvent event);
}
