package com.example.short_link.federation.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// totalItems is omitted for followers/following so remote servers show no follower count — kurl
// hides public vanity counts.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderedCollectionResponse(
    @JsonProperty("@context") String context,
    String id,
    String type,
    Integer totalItems,
    List<Object> orderedItems) {}
