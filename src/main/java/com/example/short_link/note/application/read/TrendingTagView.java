package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.TrendingTag;
import java.util.List;

public record TrendingTagView(String tag, long accounts, long uses, List<Long> history) {

  static TrendingTagView of(TrendingTag tag) {
    return new TrendingTagView(tag.tag(), tag.accounts(), tag.uses(), tag.history());
  }
}
