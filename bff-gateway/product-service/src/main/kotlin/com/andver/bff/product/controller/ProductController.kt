package com.andver.bff.product.controller

import com.andver.bff.product.model.CreateProductRequest
import com.andver.bff.product.model.CursorPageResponse
import com.andver.bff.product.model.UpdateProductRequest
import com.andver.bff.product.service.ProductService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/products", produces = [MediaType.APPLICATION_JSON_VALUE])
class ProductController(
  private val productService: ProductService,
) {

  @GetMapping
  fun list(
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int,
    @RequestParam(defaultValue = "name") sort: String,
    @RequestParam(defaultValue = "false") desc: Boolean,
  ) = productService.listPaged(page, size, sort, desc)

  @GetMapping("/cursor")
  fun listCursor(
    @RequestParam(defaultValue = "0") afterId: Long,
    @RequestParam(defaultValue = "20") limit: Int,
  ): Mono<CursorPageResponse> {
    return productService.listCursor(afterId, limit)
      .map { (items, next) ->
        CursorPageResponse(items = items, nextCursor = next)
      }
  }

  @GetMapping("/popular")
  fun popular(): Flux<com.andver.bff.product.model.ProductResponse> = productService.popular()

  @GetMapping("/stats")
  fun stats() = productService.stats()

  @GetMapping("/{id}")
  fun getById(@PathVariable id: Long) =
    productService.findById(id)
      .switchIfEmpty(Mono.error(org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND)))

  @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
  @ResponseStatus(HttpStatus.CREATED)
  fun create(@RequestBody body: Mono<CreateProductRequest>) =
    body.flatMap { productService.create(it) }

  @PostMapping("/bulk", consumes = [MediaType.APPLICATION_JSON_VALUE])
  @ResponseStatus(HttpStatus.CREATED)
  fun bulk(@RequestBody body: Mono<List<CreateProductRequest>>) =
    body.flatMapMany { productService.createAll(it) }

  @PutMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun update(@PathVariable id: Long, @RequestBody body: Mono<UpdateProductRequest>) =
    body.flatMap { productService.update(id, it) }
      .switchIfEmpty(Mono.error(org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND)))
}
