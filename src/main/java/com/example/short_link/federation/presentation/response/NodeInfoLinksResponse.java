package com.example.short_link.federation.presentation.response;

import java.util.List;

public record NodeInfoLinksResponse(List<Link> links) {

  public record Link(String rel, String href) {}
}
