package com.example.short_link.link.application.dto;

public record UserAgentInfo(
    String deviceClass, String osName, String browserName, boolean bot, String botName) {

  public static UserAgentInfo unknown() {
    return new UserAgentInfo("unknown", "unknown", "unknown", false, null);
  }
}
