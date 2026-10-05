package com.example.short_link.federation.presentation.response;

import java.util.List;

public record WebFingerResponse(String subject, List<String> aliases, List<Link> links) {

  public record Link(String rel, String type, String href) {}
}
