package com.andver.bff.admin.controller

import com.andver.bff.admin.model.AdminCreateProductRequest
import com.andver.bff.admin.model.AdminUpdateProductRequest
import com.andver.bff.admin.model.AdminUpdateUserRequest
import com.andver.bff.admin.service.AdminBffService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class AdminBffController(
  private val adminBffService: AdminBffService,
) {

  @GetMapping("/dashboard")
  fun dashboard(
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
  ): Mono<com.andver.bff.admin.model.AdminDashboardResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    return adminBffService.dashboard(cid)
  }

  @GetMapping("/users")
  fun users(
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "50") size: Int,
  ): Mono<com.andver.bff.admin.model.AdminUserPageResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    return adminBffService.usersPage(cid, page, size)
  }

  @PutMapping("/users/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun updateUser(
    @PathVariable id: Long,
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
    @RequestBody body: Mono<AdminUpdateUserRequest>,
  ): Mono<com.andver.bff.admin.model.AdminUserResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    return body.flatMap { adminBffService.updateUser(id, it, cid) }
  }

  @GetMapping("/products/{id}")
  fun productById(
    @PathVariable id: Long,
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
  ): Mono<com.andver.bff.admin.model.AdminProductResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    return adminBffService.productById(id, cid)
  }

  @PostMapping("/products", consumes = [MediaType.APPLICATION_JSON_VALUE])
  @ResponseStatus(HttpStatus.CREATED)
  fun createProduct(
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
    @RequestBody body: Mono<AdminCreateProductRequest>,
  ): Mono<com.andver.bff.admin.model.AdminProductResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    return body.flatMap { adminBffService.createProduct(it, cid) }
  }

  @PutMapping("/products/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun updateProduct(
    @PathVariable id: Long,
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
    @RequestBody body: Mono<AdminUpdateProductRequest>,
  ): Mono<com.andver.bff.admin.model.AdminProductResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    return body.flatMap { adminBffService.updateProduct(id, it, cid) }
  }

  @PostMapping("/products/bulk", consumes = [MediaType.APPLICATION_JSON_VALUE])
  @ResponseStatus(HttpStatus.CREATED)
  fun bulkProducts(
    @RequestHeader(name = "X-Correlation-Id", required = false) correlationId: String?,
    @RequestBody body: Mono<List<AdminCreateProductRequest>>,
  ): Flux<com.andver.bff.admin.model.AdminProductResponse> {
    val cid = correlationId ?: java.util.UUID.randomUUID().toString()
    return body.flatMapMany { adminBffService.bulkProducts(it, cid) }
  }
}
