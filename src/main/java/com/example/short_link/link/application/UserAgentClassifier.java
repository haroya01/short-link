package com.example.short_link.link.application;

import lombok.RequiredArgsConstructor;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAgentClassifier {

  private final UserAgentAnalyzer analyzer;

  public UserAgentInfo classify(String ua) {
    if (ua == null || ua.isBlank()) {
      return UserAgentInfo.unknown();
    }
    UserAgent agent = analyzer.parse(ua);
    String device = mapDevice(agent.getValue("DeviceClass"));
    String os = nullIfUnknown(agent.getValue("OperatingSystemName"));
    String browser = nullIfUnknown(agent.getValue("AgentName"));
    boolean bot = "bot".equals(device);
    String botName = bot ? browser : null;
    return new UserAgentInfo(device, os, browser, bot, botName);
  }

  private static String mapDevice(String deviceClass) {
    return switch (deviceClass == null ? "" : deviceClass) {
      case "Phone" -> "mobile";
      case "Tablet" -> "tablet";
      case "Desktop" -> "desktop";
      case "Robot", "Robot Mobile", "Hacker", "Spider", "Cloud" -> "bot";
      default -> "unknown";
    };
  }

  private static String nullIfUnknown(String v) {
    return (v == null || v.isBlank() || v.equals("Unknown")) ? null : v;
  }
}
