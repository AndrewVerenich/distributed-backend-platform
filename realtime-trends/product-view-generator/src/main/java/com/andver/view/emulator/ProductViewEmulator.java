package com.andver.view.emulator;

import com.andver.view.model.ProductViewEvent;
import com.andver.view.producer.ProductViewProducer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ProductViewEmulator implements CommandLineRunner {

  private final ProductViewProducer producer;
  private final Long userCount;
  private final Long categoryCount;
  private final Long productCount;
  private final Random random = new Random();

  public ProductViewEmulator(
      ProductViewProducer producer,
      @Value("${views.user-count}") Long userCount,
      @Value("${views.category-count}") Long categoryCount,
      @Value("${views.product-count}") Long productCount
  ) {
    this.producer = producer;
    this.userCount = userCount;
    this.categoryCount = categoryCount;
    this.productCount = productCount;
  }

  @Override
  public void run(String... args) throws Exception {
    List<Long> userIds = new ArrayList<>();
    for (long i = 1; i <= userCount; i++) {
      userIds.add(i);
    }
    List<Long> productIds = new ArrayList<>();
    for (long i = 1; i <= productCount; i++) {
      productIds.add(i);
    }

    List<Long> categoryIds = new ArrayList<>();
    for (long i = 1; i <= categoryCount; i++) {
      categoryIds.add(i);
    }

    Map<Long, Long> productCategoryMap = new HashMap<>();
    for (Long productId : productIds) {
      Long category = categoryIds.get(random.nextInt(categoryIds.size()));
      productCategoryMap.put(productId, category);
    }

    ExecutorService executor = Executors.newFixedThreadPool(userIds.size());

    for (Long userId : userIds) {
      executor.submit(() -> {
        while (true) {
          Long productId = productIds.get(random.nextInt(productIds.size()));
          Long categoryId = productCategoryMap.get(productId);

          ProductViewEvent event = new ProductViewEvent(userId, productId, categoryId);
          producer.sendViewEvent(event);

          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      });
    }
  }
}
