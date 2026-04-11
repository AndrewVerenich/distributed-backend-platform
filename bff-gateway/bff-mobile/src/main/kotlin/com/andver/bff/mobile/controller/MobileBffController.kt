package com.andver.bff.mobile.controller

import com.andver.bff.mobile.service.MobileBffService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class MobileBffController(
  private val mobileBffService: MobileBffService,
) {

  @GetMapping("/feed")
  fun feed(
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
    @RequestHeader(name = "X-User-Id", required = false) userIdHeader: String?,
  ): Mono<com.andver.bff.mobile.model.MobileFeedResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    val uid = userIdHeader?.toLongOrNull() ?: DEFAULT_USER_ID
    return mobileBffService.feed(cid, uid)
  }

  @GetMapping("/products")
  fun products(
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
    @RequestParam(defaultValue = "0") cursor: Long,
    @RequestParam(defaultValue = "20") limit: Int,
  ): Mono<com.andver.bff.mobile.model.MobileProductCursorResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    return mobileBffService.productsCursor(cid, cursor, limit)
  }

  @GetMapping("/products/{id}")
  fun productById(
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
    @PathVariable id: Long,
  ): Mono<com.andver.bff.mobile.model.MobileProductCompact> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    return mobileBffService.productById(cid, id)
  }

  companion object {
    private const val DEFAULT_USER_ID = 1L
  }
}
