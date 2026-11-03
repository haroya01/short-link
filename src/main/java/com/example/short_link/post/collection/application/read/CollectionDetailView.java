package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.collection.domain.CollectionKind;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CollectionDetailView(
    Long id,
    String title,
    String description,
    String visibility,
    boolean ordered,
    String curatorUsername,
    Long curatorUserId,
    List<ConnectionView> connections,
    String unlistedUrl) {

  @JsonProperty
  public String kind() {
    return CollectionKind.of(ordered).name();
  }
}
