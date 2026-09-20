package com.andver.waitingroom.gatling;

final class LoadConfig {
  private LoadConfig() {}

  static String waitingRoomUrl() {
    return System.getProperty("WAITING_ROOM_URL", "http://localhost:8100");
  }

  static String checkoutUrl() {
    return System.getProperty("CHECKOUT_URL", "http://localhost:8101");
  }

  static String eventId() {
    return System.getProperty("EVENT_ID", "flash-sale-1");
  }

  static int users() {
    return Integer.getInteger("USERS", 100);
  }

  static int durationSeconds() {
    return Integer.getInteger("DURATION_SECONDS", 60);
  }

  static int maxPolls() {
    return Integer.getInteger("MAX_POLLS", 120);
  }
}
