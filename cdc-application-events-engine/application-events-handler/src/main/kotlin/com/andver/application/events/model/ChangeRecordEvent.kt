package com.andver.application.events.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("kotlin:S117")
data class ChangeRecordEvent(
  val op: Operation,
  val before: Map<String, Any?>?,
  val after: Map<String, Any?>?,
  val source: Source
) {

  @JsonIgnore
  val updatedFields: Map<String, ChangedValue> = findDifferentValues(before, after)

  private fun findDifferentValues(first: Map<String, Any?>?, second: Map<String, Any?>?): Map<String, ChangedValue> {
    if (first == null || second == null) {
      return emptyMap()
    }
    return first.keys.union(second.keys)
      .mapNotNull { key ->
        val beforeValue = first[key]
        val afterValue = second[key]
        if (beforeValue != afterValue) {
          key to ChangedValue(beforeValue, afterValue)
        } else {
          null
        }
      }.toMap()
  }
}

data class Source(
  val db: String,
  val table: String
)

data class ChangedValue(
  val before: Any?,
  val after: Any?
)

enum class Operation {
  @JsonProperty("c")
  CREATE,

  @JsonProperty("u")
  UPDATE,

  @JsonProperty("d")
  DELETE,

  @JsonProperty("r")
  READ,
}
