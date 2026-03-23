package com.andver.hash.router.node

data class BackendNode(
  val id: String,
  val host: String,
  val port: Int,
  val weight: Int = 1,
) {
  fun baseUrl(): String = "http://$host:$port"
}
