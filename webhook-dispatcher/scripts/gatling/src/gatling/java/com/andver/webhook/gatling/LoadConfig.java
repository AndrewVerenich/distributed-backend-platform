package com.andver.webhook.gatling;

public final class LoadConfig {
  private LoadConfig() {}

  public static final String API_URL = System.getProperty("WEBHOOK_API_URL", "http://localhost:8240");
  public static final int USERS = Integer.getInteger("USERS", 20);
}
