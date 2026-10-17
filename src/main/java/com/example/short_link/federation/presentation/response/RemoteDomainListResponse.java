package com.example.short_link.federation.presentation.response;

import java.util.List;

public record RemoteDomainListResponse(List<Item> items) {

  public record Item(String domain) {}

  public static RemoteDomainListResponse of(List<String> domains) {
    return new RemoteDomainListResponse(domains.stream().map(Item::new).toList());
  }
}
