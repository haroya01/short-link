package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.SeriesItemType;
import java.util.List;

public record SetSeriesItemsCommand(Long userId, Long seriesId, List<Item> items) {

  public record Item(SeriesItemType type, Long id) {

    public Item {
      if (type == null) throw new IllegalArgumentException("item type required");
      if (id == null) throw new IllegalArgumentException("item id required");
    }
  }

  public SetSeriesItemsCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (seriesId == null) throw new IllegalArgumentException("seriesId required");
    items = items == null ? List.of() : List.copyOf(items);
    if (items.size() != items.stream().distinct().count()) {
      throw new IllegalArgumentException("items must be unique");
    }
  }
}
