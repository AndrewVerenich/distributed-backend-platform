package com.andver.hash.router.hash

interface HashFunction {
  fun hash(value: String): Long
}

class MurmurHash3Function : HashFunction {
  override fun hash(value: String): Long {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val c1 = -0x3361d2af
    val c2 = 0x1b873593
    var h1 = 0
    val roundedEnd = bytes.size and 0xfffffffc.toInt()
    var i = 0

    while (i < roundedEnd) {
      var k1 = (bytes[i].toInt() and 0xff) or
          ((bytes[i + 1].toInt() and 0xff) shl 8) or
          ((bytes[i + 2].toInt() and 0xff) shl 16) or
          (bytes[i + 3].toInt() shl 24)
      i += 4

      k1 *= c1
      k1 = Integer.rotateLeft(k1, 15)
      k1 *= c2

      h1 = h1 xor k1
      h1 = Integer.rotateLeft(h1, 13)
      h1 = h1 * 5 + -0x19ab949c
    }

    var k1 = 0
    when (bytes.size and 0x03) {
      3 -> {
        k1 = (bytes[roundedEnd + 2].toInt() and 0xff) shl 16
        k1 = k1 or ((bytes[roundedEnd + 1].toInt() and 0xff) shl 8)
        k1 = k1 or (bytes[roundedEnd].toInt() and 0xff)
      }

      2 -> {
        k1 = (bytes[roundedEnd + 1].toInt() and 0xff) shl 8
        k1 = k1 or (bytes[roundedEnd].toInt() and 0xff)
      }

      1 -> {
        k1 = bytes[roundedEnd].toInt() and 0xff
      }
    }

    if ((bytes.size and 0x03) != 0) {
      k1 *= c1
      k1 = Integer.rotateLeft(k1, 15)
      k1 *= c2
      h1 = h1 xor k1
    }

    h1 = h1 xor bytes.size
    h1 = fmix(h1)
    return h1.toLong() and 0xffffffffL
  }

  private fun fmix(h: Int): Int {
    var x = h
    x = x xor (x ushr 16)
    x *= -0x7a143595
    x = x xor (x ushr 13)
    x *= -0x3d4d51cb
    x = x xor (x ushr 16)
    return x
  }
}
