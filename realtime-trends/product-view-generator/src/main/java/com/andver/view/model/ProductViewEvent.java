package com.andver.view.model;

public record ProductViewEvent(
    long userId,
    long productId,
    long categoryId,
    long timestamp
) {

  public ProductViewEvent(long userId, long productId, long categoryId) {
    this(userId, productId, categoryId, System.currentTimeMillis());
  }
}
