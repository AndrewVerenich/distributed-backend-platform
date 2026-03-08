package com.andver.clientdeduplicator.starter.fingerprint

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultFingerprintGeneratorTest {

  private lateinit var generator: DefaultFingerprintGenerator

  @BeforeEach
  fun setUp() {
    generator = DefaultFingerprintGenerator()
  }

  @Test
  fun `same inputs always produce the same fingerprint`() {
    val body = mapOf("orderId" to 123, "amount" to 99.99)
    val fp1 = generator.generate("POST", "http://service/orders", body, emptySet(), emptySet())
    val fp2 = generator.generate("POST", "http://service/orders", body, emptySet(), emptySet())

    assertThat(fp1).isEqualTo(fp2)
  }

  @Test
  fun `fingerprint is a 64-character hex SHA-256 string`() {
    val fp = generator.generate("GET", "http://service/items", null, emptySet(), emptySet())

    assertThat(fp).hasSize(64)
    assertThat(fp).matches("[0-9a-f]{64}")
  }

  @Test
  fun `different HTTP methods produce different fingerprints`() {
    val fpGet = generator.generate("GET", "http://service/items", null, emptySet(), emptySet())
    val fpPost = generator.generate("POST", "http://service/items", null, emptySet(), emptySet())
    val fpPut = generator.generate("PUT", "http://service/items", null, emptySet(), emptySet())

    assertThat(setOf(fpGet, fpPost, fpPut)).hasSize(3)
  }

  @Test
  fun `different URIs produce different fingerprints`() {
    val fp1 = generator.generate("GET", "http://service/orders", null, emptySet(), emptySet())
    val fp2 = generator.generate("GET", "http://service/users", null, emptySet(), emptySet())

    assertThat(fp1).isNotEqualTo(fp2)
  }

  @Test
  fun `query parameters affect the fingerprint`() {
    val fp1 = generator.generate("GET", "http://service/items?page=1", null, emptySet(), emptySet())
    val fp2 = generator.generate("GET", "http://service/items?page=2", null, emptySet(), emptySet())

    assertThat(fp1).isNotEqualTo(fp2)
  }

  @Test
  fun `query parameters are sorted before hashing (order independent)`() {
    val fp1 = generator.generate("GET", "http://s/items?a=1&b=2", null, emptySet(), emptySet())
    val fp2 = generator.generate("GET", "http://s/items?b=2&a=1", null, emptySet(), emptySet())

    assertThat(fp1).isEqualTo(fp2)
  }

  @Test
  fun `excluded query params do not affect the fingerprint`() {
    val fpWithTs = generator.generate(
      "GET", "http://service/items?ts=1234567890", null, emptySet(), setOf("ts")
    )
    val fpWithoutTs = generator.generate(
      "GET", "http://service/items", null, emptySet(), setOf("ts")
    )

    assertThat(fpWithTs).isEqualTo(fpWithoutTs)
  }

  @Test
  fun `multiple excluded query params are all stripped`() {
    val fp1 = generator.generate(
      "GET", "http://s/items?nonce=abc&ts=123&page=1", null, emptySet(), setOf("nonce", "ts")
    )
    val fp2 = generator.generate(
      "GET", "http://s/items?page=1", null, emptySet(), setOf("nonce", "ts")
    )

    assertThat(fp1).isEqualTo(fp2)
  }

  @Test
  fun `different body contents produce different fingerprints`() {
    val body1 = mapOf("amount" to 100)
    val body2 = mapOf("amount" to 200)

    val fp1 = generator.generate("POST", "http://service/pay", body1, emptySet(), emptySet())
    val fp2 = generator.generate("POST", "http://service/pay", body2, emptySet(), emptySet())

    assertThat(fp1).isNotEqualTo(fp2)
  }

  @Test
  fun `null body and empty body string produce consistent fingerprint`() {
    val fpNull = generator.generate("POST", "http://service/pay", null, emptySet(), emptySet())

    assertThat(fpNull).isNotBlank()
  }

  @Test
  fun `excluded body fields do not affect the fingerprint`() {
    val body1 = mapOf("orderId" to 1, "requestId" to "abc-123", "amount" to 99)
    val body2 = mapOf("orderId" to 1, "requestId" to "xyz-789", "amount" to 99)

    val fp1 = generator.generate("POST", "http://service/order", body1, setOf("requestId"), emptySet())
    val fp2 = generator.generate("POST", "http://service/order", body2, setOf("requestId"), emptySet())

    assertThat(fp1).isEqualTo(fp2)
  }

  @Test
  fun `nested excluded fields are removed from all levels`() {
    val body1 = mapOf("data" to mapOf("id" to 1, "timestamp" to "2024-01-01"))
    val body2 = mapOf("data" to mapOf("id" to 1, "timestamp" to "2024-12-31"))

    val fp1 = generator.generate("POST", "http://s/x", body1, setOf("timestamp"), emptySet())
    val fp2 = generator.generate("POST", "http://s/x", body2, setOf("timestamp"), emptySet())

    assertThat(fp1).isEqualTo(fp2)
  }

  @Test
  fun `excluded field that does not exist in body is handled gracefully`() {
    val body = mapOf("orderId" to 1)

    val fp1 = generator.generate("POST", "http://s/x", body, setOf("nonExistentField"), emptySet())
    val fp2 = generator.generate("POST", "http://s/x", body, emptySet(), emptySet())

    assertThat(fp1).isEqualTo(fp2)
  }

  @Test
  fun `JSON object with same fields in different order produces same fingerprint`() {
    val body1 = linkedMapOf("b" to 2, "a" to 1)
    val body2 = linkedMapOf("a" to 1, "b" to 2)

    val fp1 = generator.generate("POST", "http://s/x", body1, emptySet(), emptySet())
    val fp2 = generator.generate("POST", "http://s/x", body2, emptySet(), emptySet())

    assertThat(fp1).isEqualTo(fp2)
  }

  @Test
  fun `array body with same elements produces the same fingerprint`() {
    val body1 = listOf(mapOf("id" to 1), mapOf("id" to 2))
    val body2 = listOf(mapOf("id" to 1), mapOf("id" to 2))

    val fp1 = generator.generate("POST", "http://s/batch", body1, emptySet(), emptySet())
    val fp2 = generator.generate("POST", "http://s/batch", body2, emptySet(), emptySet())

    assertThat(fp1).isEqualTo(fp2)
  }

  @Test
  fun `excluded fields in array elements are stripped`() {
    val body1 = listOf(mapOf("id" to 1, "ts" to "now"), mapOf("id" to 2, "ts" to "later"))
    val body2 = listOf(mapOf("id" to 1, "ts" to "past"), mapOf("id" to 2, "ts" to "future"))

    val fp1 = generator.generate("POST", "http://s/batch", body1, setOf("ts"), emptySet())
    val fp2 = generator.generate("POST", "http://s/batch", body2, setOf("ts"), emptySet())

    assertThat(fp1).isEqualTo(fp2)
  }
}
