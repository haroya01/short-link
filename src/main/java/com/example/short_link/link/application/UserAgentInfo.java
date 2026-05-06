package com.example.short_link.link.application;

public record UserAgentInfo(String deviceClass, String osName, String browserName, boolean bot) {

  public static UserAgentInfo unknown() {
    return new UserAgentInfo("unknown", "unknown", "unknown", false);
  }
}
