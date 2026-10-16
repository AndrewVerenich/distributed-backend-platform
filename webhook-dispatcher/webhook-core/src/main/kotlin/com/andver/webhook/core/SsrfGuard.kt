package com.andver.webhook.core

import java.net.InetAddress
import java.net.URI

object SsrfGuard {
  /**
   * Always blocks link-local / metadata. Private RFC1918 addresses are allowed
   * when [allowPrivate] is true (docker demo-receiver).
   */
  fun validate(url: String, allowPrivate: Boolean = true): Result<URI> {
    val uri = runCatching { URI(url) }.getOrElse {
      return Result.failure(IllegalArgumentException("invalid url: $url"))
    }
    if (uri.scheme != "http" && uri.scheme != "https") {
      return Result.failure(IllegalArgumentException("scheme must be http/https"))
    }
    val host = uri.host ?: return Result.failure(IllegalArgumentException("missing host"))
    if (host.equals("metadata.google.internal", ignoreCase = true) ||
      host.equals("metadata", ignoreCase = true)
    ) {
      return Result.failure(IllegalArgumentException("metadata host blocked"))
    }
    val addresses = runCatching { InetAddress.getAllByName(host) }.getOrElse {
      return Result.failure(IllegalArgumentException("cannot resolve host: $host"))
    }
    for (addr in addresses) {
      if (addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress ||
        addr.isMulticastAddress
      ) {
        // loopback allowed only for local non-docker tests when allowPrivate
        if (!(allowPrivate && addr.isLoopbackAddress)) {
          return Result.failure(IllegalArgumentException("blocked address: ${addr.hostAddress}"))
        }
      }
      if (!allowPrivate && isPrivate(addr)) {
        return Result.failure(IllegalArgumentException("private address blocked: ${addr.hostAddress}"))
      }
    }
    return Result.success(uri)
  }

  private fun isPrivate(addr: InetAddress): Boolean {
    val b = addr.address
    if (b.size != 4) return addr.isSiteLocalAddress
    val a0 = b[0].toInt() and 0xff
    val a1 = b[1].toInt() and 0xff
    return when {
      a0 == 10 -> true
      a0 == 172 && a1 in 16..31 -> true
      a0 == 192 && a1 == 168 -> true
      else -> addr.isSiteLocalAddress
    }
  }
}
