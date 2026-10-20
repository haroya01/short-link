package com.example.short_link.search.application.read;

import com.example.short_link.search.domain.RelatedTag;
import java.util.List;

public record RelatedSearchView(List<RelatedTag> tags) {

  static final RelatedSearchView NONE = new RelatedSearchView(List.of());
}
