package com.andver.webhook.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HmacSigner {
  private const val ALGORITHM = "HmacSHA256"

  fun sign(secret: String, timestamp: String, body: String): String {
    val mac = Mac.getInstance(ALGORITHM)
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM))
    val digest = mac.doFinal("$timestamp.$body".toByteArray(Charsets.UTF_8))
    return "sha256=" + digest.joinToString("") { "%02x".format(it) }
  }

  fun matches(secret: String, timestamp: String, body: String, signature: String): Boolean {
    val expected = sign(secret, timestamp, body)
    if (expected.length != signature.length) return false
    var result = 0
    for (i in expected.indices) {
      result = result or (expected[i].code xor signature[i].code)
    }
    return result == 0
  }
}
