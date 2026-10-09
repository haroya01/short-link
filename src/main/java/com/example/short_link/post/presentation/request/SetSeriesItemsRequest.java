package com.example.short_link.post.presentation.request;

import com.example.short_link.post.application.write.SetSeriesItemsCommand;
import com.example.short_link.post.domain.SeriesItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SetSeriesItemsRequest(@NotNull @Size(max = 500) List<@NotNull @Valid Item> items) {

  public record Item(@NotNull SeriesItemType type, @NotNull Long id) {}

  public List<SetSeriesItemsCommand.Item> toCommandItems() {
    return items.stream().map(i -> new SetSeriesItemsCommand.Item(i.type(), i.id())).toList();
  }
}
