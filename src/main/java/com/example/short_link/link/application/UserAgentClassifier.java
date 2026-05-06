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
    String rawDeviceClass = agent.getValue("DeviceClass");
    String os = agent.getValue("OperatingSystemName");
    String browser = agent.getValue("AgentName");
    boolean bot = isBot(rawDeviceClass);
    return new UserAgentInfo(mapDevice(rawDeviceClass), nullIfEmpty(os), nullIfEmpty(browser), bot);
  }

  private static boolean isBot(String deviceClass) {
    if (deviceClass == null) {
      return false;
    }
    return deviceClass.equalsIgnoreCase("Robot")
        || deviceClass.equalsIgnoreCase("Robot Mobile")
        || deviceClass.equalsIgnoreCase("Hacker")
        || deviceClass.equalsIgnoreCase("Spider")
        || deviceClass.equalsIgnoreCase("Cloud");
  }

  private static String mapDevice(String deviceClass) {
    if (deviceClass == null) {
      return "unknown";
    }
    return switch (deviceClass) {
      case "Phone" -> "mobile";
      case "Tablet" -> "tablet";
      case "Desktop" -> "desktop";
      case "Robot", "Robot Mobile", "Hacker", "Spider", "Cloud" -> "bot";
      default -> "unknown";
    };
  }

  private static String nullIfEmpty(String v) {
    return (v == null || v.isBlank() || v.equals("Unknown")) ? null : v;
  }
}
