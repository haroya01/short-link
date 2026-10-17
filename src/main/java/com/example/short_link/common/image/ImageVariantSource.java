package com.example.short_link.common.image;

import java.util.List;

// The slice that stores references to originals implements this, so the sweep reads rows instead
// of listing the bucket.
public interface ImageVariantSource {

  Page after(long cursor, int limit);

  List<String> newest(int limit);

  record Page(long cursor, List<String> keys) {

    public Page {
      keys = List.copyOf(keys);
    }
  }
}
