package com.andver.order.controller

import com.andver.order.model.TrendyProduct
import com.andver.order.service.ProductService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController
class ProductController(
  private val productService: ProductService
) {
  @GetMapping("/trendy-products")
  fun trendyProducts(@RequestParam userId: Long): Flux<TrendyProduct> {
    return productService.getTrendyProducts(userId)
  }
}