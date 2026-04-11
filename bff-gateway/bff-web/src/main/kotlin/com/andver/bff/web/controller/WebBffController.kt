package com.andver.bff.web.controller

import com.andver.bff.web.model.WebDashboardResponse
import com.andver.bff.web.model.WebProductPageResponse
import com.andver.bff.web.model.WebProductResponse
import com.andver.bff.web.model.WebProfileResponse
import com.andver.bff.web.service.WebBffService
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
class WebBffController(
  private val webBffService: WebBffService,
) {

  @GetMapping("/dashboard")
  fun dashboard(
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
    @RequestHeader(name = "X-User-Id", required = false) userIdHeader: String?,
  ): Mono<WebDashboardResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    val uid = userIdHeader?.toLongOrNull() ?: DEFAULT_USER_ID
    return webBffService.dashboard(cid, uid)
  }

  @GetMapping("/products")
  fun products(
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int,
    @RequestParam(defaultValue = "name") sort: String,
    @RequestParam(defaultValue = "false") desc: Boolean,
  ): Mono<WebProductPageResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    return webBffService.productPage(cid, page, size, sort, desc)
  }

  @GetMapping("/products/{id}")
  fun productById(
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
    @PathVariable id: Long,
  ): Mono<WebProductResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    return webBffService.productById(cid, id)
  }

  @GetMapping("/users/me")
  fun me(
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
    @RequestHeader(name = "X-User-Id", required = false) userIdHeader: String?,
  ): Mono<WebProfileResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    val uid = userIdHeader?.toLongOrNull() ?: DEFAULT_USER_ID
    return webBffService.userMe(cid, uid)
  }

  companion object {
    private const val DEFAULT_USER_ID = 1L
  }
}
