package com.example.short_link.post.presentation.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReplaceBlocksRequest(@NotNull @Valid List<BlockItem> blocks) {

  public record BlockItem(@NotEmpty String type, @Size(max = 100_000) String content) {}
}
