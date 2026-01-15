package com.andver.clientdeduplicator.starter.fingerprint

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.security.MessageDigest

interface FingerprintGenerator {
  fun generate(
    method: String,
    uri: String,
    body: Any?,
    excludeFields: Set<String>,
    excludeQueryParams: Set<String>,
  ): String
}

class DefaultFingerprintGenerator : FingerprintGenerator {
  private val mapper: ObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

  override fun generate(
    method: String,
    uri: String,
    body: Any?,
    excludeFields: Set<String>,
    excludeQueryParams: Set<String>
  ): String {
    val normalizedUri = normalizeUri(uri, excludeQueryParams)
    val canonicalBody = canonicalizeJson(body, excludeFields)
    val raw = "$method|$normalizedUri|$canonicalBody"
    return sha256(raw)
  }

  private fun canonicalizeJson(body: Any?, exclude: Set<String>): String {
    if (body == null) return ""

    return try {
      val tree = mapper.readTree(mapper.writeValueAsString(body))
      val cleaned = removeExcluded(tree, exclude)
      mapper.writeValueAsString(cleaned)
    } catch (_: Exception) {
      body.toString()
    }
  }

  private fun removeExcluded(node: JsonNode, exclude: Set<String>): JsonNode {
    if (node.isObject) {
      val obj = node.deepCopy<ObjectNode>()
      exclude.forEach { obj.remove(it) }

      obj.fieldNames().forEachRemaining { field ->
        obj.set<JsonNode>(field, removeExcluded(obj[field], exclude))
      }
      return obj
    }

    if (node.isArray) {
      val arr = node.deepCopy<ArrayNode>()
      for (i in 0 until arr.size()) {
        arr[i] = removeExcluded(arr[i], exclude)
      }
      return arr
    }

    return node
  }

  private fun normalizeUri(uri: String, exclude: Set<String>): String {
    val parsed = java.net.URI(uri)
    val path = parsed.path

    val query = parsed.query
      ?.split("&")
      ?.mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq == -1) return@mapNotNull null
        val key = part.substring(0, eq)
        val value = part.substring(eq + 1)
        if (key in exclude) null else key to value
      }
      ?.sortedBy { it.first }
      ?.joinToString("&") { "${it.first}=${it.second}" }

    return if (query.isNullOrEmpty()) path else "$path?$query"
  }

  private fun sha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray())
      .joinToString("") { "%02x".format(it) }
  }
}