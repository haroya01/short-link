package com.example.short_link.common.image;

import java.util.List;

public record ImageVariantsRequested(List<String> keys) {

  public ImageVariantsRequested {
    keys = List.copyOf(keys);
  }

  public static ImageVariantsRequested of(String key) {
    return new ImageVariantsRequested(List.of(key));
  }
}
