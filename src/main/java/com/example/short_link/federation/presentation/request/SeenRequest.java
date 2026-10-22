package com.example.short_link.federation.presentation.request;

import java.util.List;

public record SeenRequest(List<String> uris, List<Dwell> dwell, String feed) {

  public record Dwell(String uri, Long ms) {}
}
