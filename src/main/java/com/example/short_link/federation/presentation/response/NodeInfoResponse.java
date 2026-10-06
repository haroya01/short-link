package com.example.short_link.federation.presentation.response;

import java.util.List;
import java.util.Map;

public record NodeInfoResponse(
    String version,
    Software software,
    List<String> protocols,
    Services services,
    boolean openRegistrations,
    Usage usage,
    Map<String, Object> metadata) {

  public record Software(String name, String version, String repository, String homepage) {}

  public record Services(List<String> inbound, List<String> outbound) {}

  public record Usage(Map<String, Object> users, int localPosts) {}
}
