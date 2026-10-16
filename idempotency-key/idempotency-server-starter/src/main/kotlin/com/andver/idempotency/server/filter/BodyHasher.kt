package com.andver.idempotency.server.filter

import java.security.MessageDigest
import java.util.HexFormat

object BodyHasher {
  fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return HexFormat.of().formatHex(digest)
  }
}
