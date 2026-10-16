package com.andver.booking.gatling;

final class LoadConfig {
  private LoadConfig() {}

  static String bookingUrl() {
    return System.getProperty("BOOKING_URL", "http://localhost:8230");
  }

  static String seatId() {
    return System.getProperty("SEAT_ID", "last-1");
  }

  static int users() {
    return Integer.parseInt(System.getProperty("USERS", "40"));
  }

  static long holdTtlMs() {
    return Long.parseLong(System.getProperty("HOLD_TTL_MS", "5000"));
  }
}
