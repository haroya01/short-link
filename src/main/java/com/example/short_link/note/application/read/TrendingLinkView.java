package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.TrendingLink;
import java.util.List;

public record TrendingLinkView(
    String url,
    String title,
    String description,
    String imageUrl,
    long accounts,
    long uses,
    List<Long> history) {

  static TrendingLinkView of(TrendingLink link) {
    return new TrendingLinkView(
        link.url(),
        link.title(),
        link.description(),
        link.imageUrl(),
        link.accounts(),
        link.uses(),
        link.history());
  }
}
