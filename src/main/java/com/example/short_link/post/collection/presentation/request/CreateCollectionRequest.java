package com.example.short_link.post.collection.presentation.request;

import com.example.short_link.post.collection.domain.CollectionKind;
import com.example.short_link.post.collection.domain.CollectionVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCollectionRequest(
    @NotBlank @Size(max = 120) String title,
    @Size(max = 280) String description,
    CollectionVisibility visibility,
    CollectionKind kind,
    Boolean ordered) {

  // kind는 ordered 이전에 나간 앱이 보내는 값이다. 둘 다 오면 ordered를 따른다.
  public boolean orderedOrLegacyPath() {
    return ordered != null ? ordered : kind == CollectionKind.PATH;
  }
}
