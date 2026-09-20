package com.andver.waitingroom.queue

object RedisKeys {
  const val EVENTS = "wr:events"

  fun seq(eventId: String) = "wr:$eventId:seq"
  fun queue(eventId: String) = "wr:$eventId:q"
  fun visitor(eventId: String, visitorId: String) = "wr:$eventId:v:$visitorId"
  fun meta(eventId: String) = "wr:$eventId:meta"
  fun ticket(ticketId: String) = "wr:ticket:$ticketId"
  fun admitLock(eventId: String) = "wr:admit:$eventId:lock"
}
